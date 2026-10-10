package com.ejden.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class HistoryFilter(val id: String) {
    All("all"), Today("today"), Paid("paid"), Credit("credit"), Cancelled("cancelled")
}

fun paymentLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.Cash -> "Espèces"
    PaymentMethod.MobileMoney -> "Mobile Money"
    PaymentMethod.Card -> "Carte"
    PaymentMethod.Other -> "Autre"
    PaymentMethod.Credit -> "Crédit"
}

fun saleNumber(sale: Sale): String = "N° " + sale.id.takeLast(6).uppercase()

/** Bénéfice de la vente, ou null si un prix d'achat est inconnu. */
fun saleMargin(sale: Sale): Double? {
    if (sale.items.any { it.unitCost == null }) return null
    val gross = sale.items.sumOf { (it.unitPrice - (it.unitCost ?: 0.0)) * it.quantity }
    return gross - sale.discount
}

private fun localDateOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

fun saleDate(sale: Sale, zone: ZoneId): LocalDate? =
    parseDateMillis(sale.createdAt)?.let { localDateOf(it, zone) }

private fun matchesFilter(
    sale: Sale,
    remaining: Double,
    filter: HistoryFilter,
    today: LocalDate,
    zone: ZoneId
): Boolean = when (filter) {
    HistoryFilter.Today -> saleDate(sale, zone) == today
    HistoryFilter.Paid -> !sale.isCancelled && remaining <= 0.0
    HistoryFilter.Credit -> !sale.isCancelled && remaining > 0.0
    HistoryFilter.Cancelled -> sale.cancelledAt != null
    HistoryFilter.All -> true
}

/** query : déjà nettoyée (sans espaces autour, en minuscules). */
private fun matchesSearch(sale: Sale, query: String): Boolean {
    if (query.isEmpty()) return true
    val haystack = (
        listOf(
            sale.id,
            saleNumber(sale),
            sale.borrowerName ?: "",
            paymentLabel(sale.paymentMethod)
        ) + sale.items.map { it.productName }
        ).joinToString(" ").lowercase()
    return haystack.contains(query)
}

/** Ventes filtrées, la plus récente d'abord. */
fun filterSales(
    sales: List<Sale>,
    remaining: Map<String, Double>,
    filter: HistoryFilter,
    query: String,
    today: LocalDate,
    zone: ZoneId
): List<Sale> = sales
    .filter { matchesFilter(it, remaining[it.id] ?: 0.0, filter, today, zone) && matchesSearch(it, query) }
    .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }

data class HistorySummary(val count: Int, val total: Double, val remaining: Double)

/** Résumé de ce qui est affiché : les ventes annulées ne comptent pas. */
fun summarizeHistory(sales: List<Sale>, remaining: Map<String, Double>): HistorySummary {
    val counted = sales.filter { !it.isCancelled }
    return HistorySummary(
        counted.size,
        counted.sumOf { it.total },
        counted.sumOf { remaining[it.id] ?: 0.0 }
    )
}

data class DayGroup(val key: String, val label: String, val sales: List<Sale>)

private fun capitalizeWords(text: String): String =
    text.split(" ").joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Aujourd'hui"
    today.minusDays(1) -> "Hier"
    else -> capitalizeWords(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE).format(date))
}

/** Regroupe des ventes déjà triées par jour. */
fun groupSalesByDay(sales: List<Sale>, today: LocalDate, zone: ZoneId): List<DayGroup> {
    val groups = ArrayList<DayGroup>()
    var currentKey = ""
    var currentLabel = ""
    var current = ArrayList<Sale>()

    fun flush() {
        if (current.isNotEmpty()) groups.add(DayGroup(currentKey, currentLabel, current))
    }

    for (sale in sales) {
        val date = saleDate(sale, zone)
        val key = date?.toString() ?: "inconnu"
        if (key != currentKey) {
            flush()
            currentKey = key
            currentLabel = if (date == null) "Date inconnue" else dayLabel(date, today)
            current = ArrayList()
        }
        current.add(sale)
    }
    flush()
    return groups
}

private fun format(createdAt: String, pattern: String, zone: ZoneId): String {
    val millis = parseDateMillis(createdAt) ?: return ""
    return DateTimeFormatter.ofPattern(pattern, Locale.FRANCE).format(Instant.ofEpochMilli(millis).atZone(zone))
}

fun timeText(createdAt: String, zone: ZoneId): String = format(createdAt, "HH:mm", zone)

fun shortDateText(createdAt: String, zone: ZoneId): String = format(createdAt, "dd/MM/yyyy", zone)

fun fullDateText(createdAt: String, zone: ZoneId): String = format(createdAt, "d MMMM yyyy 'à' HH:mm", zone)

/** Texte du reçu, prêt à être partagé (WhatsApp, SMS...). */
fun buildReceipt(
    sale: Sale,
    shop: ShopSettings,
    remainingNow: Double,
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val lines = ArrayList<String>()

    if (shop.name.isNotEmpty()) lines.add("*${shop.name}*")
    if (shop.phone.isNotEmpty()) lines.add("Tél : ${shop.phone}")
    if (shop.name.isNotEmpty() || shop.phone.isNotEmpty()) lines.add("")

    lines.add("*REÇU DE VENTE*")
    lines.add(saleNumber(sale))
    lines.add(fullDateText(sale.createdAt, zone))
    if (!sale.borrowerName.isNullOrEmpty()) lines.add("Client : ${sale.borrowerName}")
    lines.add("")

    for (item in sale.items) {
        lines.add("${plainNumber(item.quantity)} \u00D7 ${item.productName} \u2014 ${formatMoney(item.total)}")
    }
    lines.add("")

    if (sale.discount > 0.0) {
        lines.add("Sous-total : ${formatMoney(sale.subtotal)}")
        lines.add("Remise : \u2212 ${formatMoney(sale.discount)}")
    }
    lines.add("*TOTAL : ${formatMoney(sale.total)}*")
    lines.add("Paiement : ${paymentLabel(sale.paymentMethod)}")
    lines.add("Payé : ${formatMoney(sale.amountPaid)}")
    if (sale.change > 0.0) lines.add("Monnaie rendue : ${formatMoney(sale.change)}")

    val refunded = sale.remaining - remainingNow
    if (refunded > 0.0) lines.add("Déjà remboursé : ${formatMoney(refunded)}")
    if (remainingNow > 0.0) {
        lines.add("*Reste à payer : ${formatMoney(remainingNow)}*")
    } else if (sale.remaining > 0.0) {
        lines.add("*Crédit soldé*")
    }

    lines.add("")
    lines.add("Merci de votre confiance !")
    return lines.joinToString("\n")
}
