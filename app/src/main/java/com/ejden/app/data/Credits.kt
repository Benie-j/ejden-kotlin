package com.ejden.app.data

import java.time.Instant
import kotlin.math.floor

const val CREDIT_OVERDUE_DAYS = 30

data class CreditSaleEntry(val sale: Sale, val remaining: Double)

data class CreditGroup(
    /** Identifiant du client, ou « name:... » pour un nom saisi sans fiche. */
    val key: String,
    val clientId: String?,
    val name: String,
    val amount: Double,
    /** Ventes à encaisser, la plus ancienne d'abord. */
    val sales: List<CreditSaleEntry>,
    val oldestAt: String,
    val oldestDays: Int,
    val overdue: Boolean
)

data class CreditsSummary(
    val total: Double,
    val debtors: Int,
    val overdueAmount: Double,
    val overdueDebtors: Int,
    val groups: List<CreditGroup>
)

private class GroupBuilder(
    val key: String,
    val clientId: String?,
    var name: String,
    var amount: Double,
    val sales: MutableList<CreditSaleEntry>,
    val oldestAt: String
)

/** Tous les crédits non soldés, regroupés par débiteur (plus gros d'abord). */
fun computeCreditsSummary(
    sales: List<Sale>,
    payments: List<CreditPayment>,
    clients: List<Client>,
    now: Instant = Instant.now()
): CreditsSummary {
    val remaining = computeRemainingMap(sales, payments)
    val clientsById = clients.associateBy { it.id }
    val builders = LinkedHashMap<String, GroupBuilder>()

    val open = sales
        .filter { (remaining[it.id] ?: 0.0) > 0.0 }
        .sortedBy { parseDateMillis(it.createdAt) ?: 0L }

    for (sale in open) {
        val client = sale.customerId?.let { clientsById[it] }
        val name = client?.name ?: (sale.borrowerName?.jsTrim() ?: "")
        if (name.isEmpty()) continue

        // Un nom saisi qui correspond à une fiche est rattaché à cette fiche.
        val byName = client ?: clients.firstOrNull { it.name.lowercase() == name.lowercase() }
        val key = byName?.id ?: ("name:" + name.lowercase())
        val entry = CreditSaleEntry(sale, remaining[sale.id] ?: 0.0)
        val existing = builders[key]

        if (existing != null) {
            existing.sales.add(entry)
            existing.amount += entry.remaining
            // Sans fiche : on garde l'orthographe de la vente la plus récente.
            if (byName == null) existing.name = name
        } else {
            builders[key] = GroupBuilder(
                key, byName?.id, byName?.name ?: name, entry.remaining, mutableListOf(entry), sale.createdAt
            )
        }
    }

    val nowMillis = now.toEpochMilli()
    val groups = builders.values.map { b ->
        val at = parseDateMillis(b.oldestAt) ?: nowMillis
        val days = maxOf(0.0, floor((nowMillis - at) / 86_400_000.0)).toInt()
        CreditGroup(b.key, b.clientId, b.name, b.amount, b.sales, b.oldestAt, days, days > CREDIT_OVERDUE_DAYS)
    }.sortedByDescending { it.amount }

    val overdue = groups.filter { it.overdue }
    return CreditsSummary(
        total = groups.sumOf { it.amount },
        debtors = groups.size,
        overdueAmount = overdue.sumOf { it.amount },
        overdueDebtors = overdue.size,
        groups = groups
    )
}

/** Ventes non soldées d'un client (par identifiant ou par nom), la plus récente d'abord. */
fun clientDebtSales(client: Client, sales: List<Sale>, remaining: Map<String, Double>): List<Sale> {
    val name = client.name.lowercase()
    return sales
        .filter { sale ->
            val borrower = sale.borrowerName
            (remaining[sale.id] ?: 0.0) > 0.0 &&
                (sale.customerId == client.id || (borrower != null && borrower.jsTrim().lowercase() == name))
        }
        .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
}

/** Derniers paiements reçus d'un client (annulés exclus), le plus récent d'abord. */
fun clientRecentPayments(
    client: Client,
    sales: List<Sale>,
    payments: List<CreditPayment>,
    limit: Int = 5
): List<CreditPayment> {
    val name = client.name.jsTrim().lowercase()
    val ids = sales
        .filter { sale ->
            val borrower = sale.borrowerName
            sale.customerId == client.id || (borrower != null && borrower.jsTrim().lowercase() == name)
        }
        .map { it.id }
        .toSet()

    return payments
        .filter { it.saleId in ids && !it.isCancelled }
        .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
        .take(maxOf(0, limit))
}

data class PaymentOutcome(val success: Boolean, val message: String)

/** Texte à afficher après un encaissement de crédit. */
fun describePayment(result: CreditPaymentResult, amount: Double, due: Double): PaymentOutcome = when (result) {
    is CreditPaymentResult.Ok -> PaymentOutcome(
        true,
        if (amount == due) "Crédit soldé : ${formatMoney(amount)} reçus."
        else "Paiement de ${formatMoney(amount)} enregistré. Reste ${formatMoney(due - amount)}."
    )
    is CreditPaymentResult.TooMuch ->
        PaymentOutcome(false, "Ce montant dépasse ce qui est dû (${formatMoney(result.due)}).")
    CreditPaymentResult.NoDebt -> PaymentOutcome(false, "Ce crédit est déjà soldé.")
    CreditPaymentResult.StorageError ->
        PaymentOutcome(false, "Enregistrement impossible : la mémoire est pleine.")
    CreditPaymentResult.InvalidAmount, CreditPaymentResult.InvalidMethod ->
        PaymentOutcome(false, "Paiement invalide.")
}
