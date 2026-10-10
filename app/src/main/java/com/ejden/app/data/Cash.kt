package com.ejden.app.data

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

enum class CashType(val id: String) {
    In("in"), Out("out");

    companion object {
        fun fromId(value: Any?): CashType? = entries.firstOrNull { it.id == value }
    }
}

data class CashEntry(
    val id: String,
    val type: CashType,
    val amount: Double,
    val reason: String,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.id)
        put("amount", amount)
        put("reason", reason)
        put("createdAt", createdAt)
    }
}

fun normalizeCashEntry(value: Any?): CashEntry? {
    val c = value as? JSONObject ?: return null
    val reason = cleanLabel(c.opt("reason"), ModuleLimits.TEXT_MAX)
    val amount = cleanAmount(c.opt("amount"))
    val createdAt = validDate(c.opt("createdAt"))
    val type = CashType.fromId(c.opt("type"))
    val id = c.opt("id") as? String

    if (id == null || id.isEmpty() || id.length > 100 ||
        type == null || reason.isEmpty() || amount == null || createdAt == null
    ) {
        return null
    }
    return CashEntry(id, type, amount, reason, createdAt)
}

enum class CashSource {
    Sale, CreditPayment, Entry, Exit, Expense, Purchase, SupplierPayment, InvoicePayment
}

data class CashMovement(
    val id: String,
    val source: CashSource,
    /** Positif = entrée d'argent, négatif = sortie. */
    val amount: Double,
    val label: String,
    val createdAt: String,
    val method: PaymentMethod? = null,
    /** Entrée/sortie manuelle : identifiant à supprimer. */
    val deletableId: String? = null
)

data class CashSummary(
    val balance: Double,
    val todayIn: Double,
    val todayOut: Double,
    val salesByMethod: Map<PaymentMethod, Double>,
    /** Les plus récents d'abord. */
    val movements: List<CashMovement>
)

/**
 * extraMovements : mouvements venant de modules ajoutés plus tard
 * (achats, paiements fournisseurs, paiements de factures).
 */
class CashRepository(
    private val store: KeyValueStore,
    private val sales: SaleRepository,
    private val credits: CreditPaymentRepository,
    private val expenses: ExpenseRepository,
    private val extraMovements: () -> List<CashMovement> = { emptyList() }
) {
    companion object {
        const val KEY = "ejden_cash"
    }

    fun getCashEntries(): List<CashEntry> = readList(store, KEY, ::normalizeCashEntry)

    private fun save(entries: List<CashEntry>): Boolean =
        writeList(store, KEY, entries.map { it.toJson() })

    fun addCashEntry(entry: CashEntry): Boolean {
        val clean = normalizeCashEntry(entry.toJson()) ?: return false
        return save(getCashEntries() + clean)
    }

    fun deleteCashEntry(entryId: String): Boolean {
        val entries = getCashEntries()
        val remaining = entries.filter { it.id != entryId }
        if (remaining.size == entries.size) return false
        return save(remaining)
    }

    fun getCashSummary(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): CashSummary {
        val movements = ArrayList<CashMovement>()
        val byMethod = LinkedHashMap<PaymentMethod, Double>()
        for (method in PaymentMethod.entries) byMethod[method] = 0.0

        val allSales = sales.getSales()

        for (sale in allSales) {
            if (sale.isCancelled) continue
            val collected = maxOf(0.0, sale.total - sale.remaining)
            if (collected > 0.0) {
                byMethod[sale.paymentMethod] = (byMethod[sale.paymentMethod] ?: 0.0) + collected
                movements.add(
                    CashMovement(
                        id = "sale-" + sale.id,
                        source = CashSource.Sale,
                        amount = collected,
                        label = "Vente N° " + sale.id.takeLast(6).uppercase(),
                        createdAt = sale.createdAt,
                        method = sale.paymentMethod
                    )
                )
            }
        }

        // Remboursements de crédits reçus (paiements non annulés).
        val salesById = allSales.associateBy { it.id }
        for (payment in credits.getCreditPayments()) {
            val sale = salesById[payment.saleId]
            if (payment.isCancelled || sale == null || sale.isCancelled) continue

            byMethod[payment.method] = (byMethod[payment.method] ?: 0.0) + payment.amount
            val who = if (!sale.borrowerName.isNullOrEmpty()) " · " + sale.borrowerName else ""
            movements.add(
                CashMovement(
                    id = "credit-payment-" + payment.id,
                    source = CashSource.CreditPayment,
                    amount = payment.amount,
                    label = "Remboursement" + who,
                    createdAt = payment.createdAt,
                    method = payment.method
                )
            )
        }

        for (entry in getCashEntries()) {
            movements.add(
                CashMovement(
                    id = "cash-" + entry.id,
                    source = if (entry.type == CashType.In) CashSource.Entry else CashSource.Exit,
                    amount = if (entry.type == CashType.In) entry.amount else -entry.amount,
                    label = entry.reason,
                    createdAt = entry.createdAt,
                    deletableId = entry.id
                )
            )
        }

        for (expense in expenses.getExpenses()) {
            movements.add(
                CashMovement(
                    id = "expense-" + expense.id,
                    source = CashSource.Expense,
                    amount = -expense.amount,
                    label = expense.label,
                    createdAt = expense.createdAt
                )
            )
        }

        for (extra in extraMovements()) {
            movements.add(extra)
            val method = extra.method
            if (extra.source == CashSource.InvoicePayment && method != null) {
                byMethod[method] = (byMethod[method] ?: 0.0) + extra.amount
            }
        }

        val sorted = movements.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }

        val nowDate = now.atZone(zone).toLocalDate()
        var balance = 0.0
        var todayIn = 0.0
        var todayOut = 0.0

        for (movement in sorted) {
            balance += movement.amount
            val millis = parseDateMillis(movement.createdAt)
            val sameDay = millis != null &&
                Instant.ofEpochMilli(millis).atZone(zone).toLocalDate() == nowDate
            if (sameDay) {
                if (movement.amount > 0.0) todayIn += movement.amount else todayOut += -movement.amount
            }
        }

        return CashSummary(balance, todayIn, todayOut, byMethod, sorted)
    }
}
