package com.ejden.app.data

import org.json.JSONObject
import java.time.Instant

val CREDIT_PAYMENT_METHODS: List<PaymentMethod> = listOf(
    PaymentMethod.Cash, PaymentMethod.MobileMoney, PaymentMethod.Card, PaymentMethod.Other
)

const val CREDIT_PAYMENTS_KEY = "ejden_credit_payments"

data class CreditPayment(
    val id: String,
    val saleId: String,
    val amount: Double,
    val method: PaymentMethod,
    val createdAt: String,
    val cancelledAt: String? = null
) {
    val isCancelled: Boolean get() = !cancelledAt.isNullOrEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("saleId", saleId)
        put("amount", amount)
        put("method", method.id)
        put("createdAt", createdAt)
        if (cancelledAt != null) put("cancelledAt", cancelledAt)
    }
}

fun normalizeCreditPayment(value: Any?): CreditPayment? {
    val p = value as? JSONObject ?: return null
    val amount = cleanAmount(p.opt("amount"))
    val createdAt = validDate(p.opt("createdAt"))
    val id = p.opt("id") as? String
    val saleId = p.opt("saleId") as? String
    val method = PaymentMethod.fromId(p.opt("method"))

    if (id == null || id.isEmpty() || id.length > 100 ||
        saleId == null || saleId.isEmpty() || saleId.length > 100 ||
        method == null || method !in CREDIT_PAYMENT_METHODS ||
        amount == null || createdAt == null
    ) {
        return null
    }

    val cancelled = (p.opt("cancelledAt") as? String)?.takeIf { validDate(it) != null }
    return CreditPayment(id, saleId, amount, method, createdAt, cancelled)
}

fun readCreditPayments(store: KeyValueStore): List<CreditPayment> =
    readList(store, CREDIT_PAYMENTS_KEY, ::normalizeCreditPayment)

/** Reste à payer de chaque vente (clé = id de la vente). */
fun computeRemainingMap(sales: List<Sale>, payments: List<CreditPayment>): Map<String, Double> {
    val paid = HashMap<String, Double>()
    for (payment in payments) {
        if (!payment.isCancelled) {
            paid[payment.saleId] = (paid[payment.saleId] ?: 0.0) + payment.amount
        }
    }

    val remaining = LinkedHashMap<String, Double>()
    for (sale in sales) {
        remaining[sale.id] =
            if (sale.isCancelled) 0.0
            else maxOf(0.0, Math.round(sale.remaining - (paid[sale.id] ?: 0.0)).toDouble())
    }
    return remaining
}

data class AppliedPayment(val saleId: String, val amount: Double)

sealed class CreditPaymentResult {
    data class Ok(val applied: List<AppliedPayment>) : CreditPaymentResult()
    data object InvalidAmount : CreditPaymentResult()
    data object InvalidMethod : CreditPaymentResult()
    data object NoDebt : CreditPaymentResult()
    data class TooMuch(val due: Double) : CreditPaymentResult()
    data object StorageError : CreditPaymentResult()
}

sealed class CancelCreditPaymentResult {
    data object Ok : CancelCreditPaymentResult()
    data object NotFound : CancelCreditPaymentResult()
    data object AlreadyCancelled : CancelCreditPaymentResult()
    data object StorageError : CancelCreditPaymentResult()
}

class CreditPaymentRepository(
    private val store: KeyValueStore,
    private val sales: SaleRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    fun getCreditPayments(): List<CreditPayment> = readCreditPayments(store)

    fun getRemainingMap(): Map<String, Double> =
        computeRemainingMap(sales.getSales(), getCreditPayments())

    fun getSaleRemaining(sale: Sale): Double =
        computeRemainingMap(listOf(sale), getCreditPayments())[sale.id] ?: 0.0

    /** Paiements d'une vente, du plus récent au plus ancien (annulés compris). */
    fun getSalePayments(saleId: String): List<CreditPayment> =
        getCreditPayments()
            .filter { it.saleId == saleId }
            .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }

    private fun save(payments: List<CreditPayment>): Boolean =
        writeList(store, CREDIT_PAYMENTS_KEY, payments.map { it.toJson() })

    /**
     * Enregistre un remboursement réparti sur les ventes indiquées
     * (la plus ancienne d'abord). Tout passe ou rien.
     */
    fun recordCreditPayment(
        saleIds: List<String>,
        amount: Double,
        method: PaymentMethod
    ): CreditPaymentResult {
        val cleanPaid = cleanAmount(amount) ?: return CreditPaymentResult.InvalidAmount
        if (method !in CREDIT_PAYMENT_METHODS) return CreditPaymentResult.InvalidMethod

        val payments = getCreditPayments().toMutableList()
        val remaining = computeRemainingMap(sales.getSales(), payments)

        val targets = saleIds.distinct().filter { (remaining[it] ?: 0.0) > 0.0 }
        val totalDue = targets.sumOf { remaining[it] ?: 0.0 }

        if (targets.isEmpty()) return CreditPaymentResult.NoDebt
        if (cleanPaid > totalDue) return CreditPaymentResult.TooMuch(totalDue)

        val createdAt = toIso(now())
        val applied = ArrayList<AppliedPayment>()
        var left = cleanPaid

        for (saleId in targets) {
            if (left <= 0.0) break
            val part = minOf(left, remaining[saleId] ?: 0.0)
            if (part > 0.0) {
                applied.add(AppliedPayment(saleId, part))
                payments.add(CreditPayment(newId(), saleId, part, method, createdAt))
                left -= part
            }
        }

        if (!save(payments)) return CreditPaymentResult.StorageError
        return CreditPaymentResult.Ok(applied)
    }

    /** Annule un paiement saisi par erreur : il reste visible, barré. */
    fun cancelCreditPayment(paymentId: String): CancelCreditPaymentResult {
        val payments = getCreditPayments().toMutableList()
        val index = payments.indexOfFirst { it.id == paymentId }
        if (index == -1) return CancelCreditPaymentResult.NotFound
        if (payments[index].isCancelled) return CancelCreditPaymentResult.AlreadyCancelled

        payments[index] = payments[index].copy(cancelledAt = toIso(now()))
        return if (save(payments)) CancelCreditPaymentResult.Ok else CancelCreditPaymentResult.StorageError
    }
}
