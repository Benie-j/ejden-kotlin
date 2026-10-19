package com.ejden.app.data

import java.time.ZoneId

enum class ReportStatus(val id: String) {
    All("all"),
    Active("active"),
    Cancelled("cancelled"),
    Credit("credit");

    companion object {
        fun fromId(value: Any?): ReportStatus? = entries.firstOrNull { it.id == value }
    }
}

/** Chaîne vide = « tous » (comme dans le HTML). Par défaut : ventes actives. */
data class ReportFilters(
    val range: ReportRange,
    val productId: String = "",
    val category: String = "",
    val clientId: String = "",
    val method: PaymentMethod? = null,
    val status: ReportStatus = ReportStatus.Active
)

fun reportMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.Cash -> "Espèces"
    PaymentMethod.MobileMoney -> "Mobile Money"
    PaymentMethod.Card -> "Carte"
    PaymentMethod.Other -> "Autre"
    PaymentMethod.Credit -> "Crédit"
}

data class ReportLine(
    val productId: String,
    val name: String,
    val qty: Double,
    val amount: Double,
    val cost: Double?
)

data class ReportSaleView(
    val sale: Sale,
    val at: Long,
    val lines: List<ReportLine>,
    /** Montant filtré (CA de la vente, ou des seules lignes retenues par le filtre produit/catégorie). */
    val amount: Double,
    val cancelled: Boolean
)

/**
 * Applique les filtres à une période donnée (la période principale ou celle de comparaison).
 * « active » garde les annulées dans la vue : elles sont comptées à part, jamais dans le CA.
 */
fun buildSaleViews(
    sales: List<Sale>,
    filters: ReportFilters,
    range: ReportRange,
    categoryOf: Map<String, String>,
    zone: ZoneId = ZoneId.systemDefault()
): List<ReportSaleView> {
    val from = range.startMillis(zone)
    val to = range.endMillis(zone)
    val itemFilter = filters.productId != "" || filters.category != ""
    val out = ArrayList<ReportSaleView>()

    for (sale in sales) {
        val at = parseDateMillis(sale.createdAt) ?: continue
        if (at < from || at > to) continue
        if (filters.method != null && sale.paymentMethod != filters.method) continue
        if (filters.clientId != "" && sale.customerId != filters.clientId) continue

        val cancelled = sale.isCancelled
        if (filters.status == ReportStatus.Cancelled && !cancelled) continue
        if (filters.status == ReportStatus.Credit && (cancelled || sale.remaining <= 0)) continue

        var items = sale.items
        if (filters.productId != "") items = items.filter { it.productId == filters.productId }
        if (filters.category != "") {
            items = items.filter { (categoryOf[it.productId] ?: "") == filters.category }
        }
        if (itemFilter && items.isEmpty()) continue

        val lines = items.map { item ->
            ReportLine(
                item.productId,
                item.productName,
                item.quantity,
                item.total,
                item.unitCost?.let { unit -> unit * item.quantity }
            )
        }
        out.add(
            ReportSaleView(
                sale = sale,
                at = at,
                lines = lines,
                amount = if (itemFilter) lines.sumOf { it.amount } else sale.total,
                cancelled = cancelled
            )
        )
    }
    return out
}

data class ReportTotals(
    val revenue: Double,
    val count: Int,
    val units: Double,
    val average: Double,
    val cancelledCount: Int,
    val cancelledAmount: Double,
    /** null dès qu'un prix d'achat est inconnu : jamais de marge inventée. */
    val margin: Double?
)

fun computeTotals(views: List<ReportSaleView>): ReportTotals {
    val ok = views.filter { !it.cancelled }
    val cancelled = views.filter { it.cancelled }
    val revenue = ok.sumOf { it.amount }
    var cost = 0.0
    var known = true
    for (view in ok) {
        for (line in view.lines) {
            val c = line.cost
            if (c == null) known = false else cost += c
        }
    }
    return ReportTotals(
        revenue = revenue,
        count = ok.size,
        units = ok.sumOf { view -> view.lines.sumOf { it.qty } },
        average = if (ok.isNotEmpty()) revenue / ok.size else 0.0,
        cancelledCount = cancelled.size,
        cancelledAmount = cancelled.sumOf { it.amount },
        margin = if (ok.isNotEmpty() && known) revenue - cost else null
    )
}
