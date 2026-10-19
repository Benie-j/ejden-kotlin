package com.ejden.app.data

import java.time.ZoneId

/** Données brutes du rapport, lues dans le stockage par l'écran (le calcul reste pur). */
data class ReportSource(
    val sales: List<Sale>,
    val products: List<Product>,
    val clients: List<Client>,
    val creditPayments: List<CreditPayment>,
    val stockMovements: List<StockMovement>
)

data class ReportTeamUser(val name: String, val count: Int, val amount: Double, val cancelled: Int)

data class ReportTeam(val users: List<ReportTeamUser>, val note: String)

/** Le rapport complet : un seul calcul, réutilisé par l'écran, les exports et l'impression. */
data class ReportData(
    val filters: ReportFilters,
    val comparison: ReportRange?,
    val historyDays: Int,
    val totals: ReportTotals,
    val previous: ReportTotals?,
    val previousNote: String?,
    val timeline: ReportTimeline,
    val products: ReportProducts,
    val stock: ReportStock,
    val clients: ReportClients,
    val payments: ReportPayments,
    val team: ReportTeam,
    val analyses: ReportAnalyses,
    val insights: List<ReportInsight>
)

private const val REPORT_TEAM_NOTE =
    "EJDEN n'enregistre pas encore l'auteur de chaque opération : " +
        "le détail par utilisateur sera disponible avec la gestion multi-utilisateurs."

/**
 * Équivalent de computeReport (report-engine.ts).
 * filters.range = période affichée ; compare = période de comparaison (null = aucune).
 */
fun computeReport(
    source: ReportSource,
    filters: ReportFilters,
    compare: ReportRange?,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): ReportData {
    val range = filters.range
    val today = statDay(nowMillis, zone)
    val categoryOf = source.products.associate { it.id to (it.category ?: "") }

    val views = buildSaleViews(source.sales, filters, range, categoryOf, zone)
    val totals = computeTotals(views)
    val history = computeReportHistory(source.sales, today, zone)
    val comparison = computeReportComparison(source.sales, filters, compare, categoryOf, history.firstAt, zone)
    val timeline = buildReportTimeline(views, range, zone)

    val productReport = computeReportProducts(
        views,
        if (comparison.previous != null) comparison.views else null,
        source.products,
        filters,
        range,
        zone
    )
    val stock = computeReportStock(
        source.products, source.stockMovements, productReport.soldQty, filters, range, history.days, zone
    )
    val clients = computeReportClients(views, source.sales, source.clients, filters, range, nowMillis, zone)

    val remaining = computeRemainingMap(source.sales, source.creditPayments)
    val payments = computeReportPayments(
        views, totals, comparison, source.clients, source.creditPayments, remaining, filters, range, nowMillis, zone
    )

    val team = ReportTeam(
        listOf(ReportTeamUser("Utilisateur principal", totals.count, totals.revenue, totals.cancelledCount)),
        REPORT_TEAM_NOTE
    )

    val allDaily = buildReportHistorySeries(source.sales, filters, categoryOf, history.days, today, zone)
    val analyses = computeReportAnalyses(allDaily, history.days, range)
    val insights = buildReportInsights(
        range, history.days, totals, comparison.previous, analyses, stock, payments, clients, nowMillis
    )

    return ReportData(
        filters = filters,
        comparison = compare,
        historyDays = history.days,
        totals = totals,
        previous = comparison.previous,
        previousNote = comparison.note,
        timeline = timeline,
        products = productReport,
        stock = stock,
        clients = clients,
        payments = payments,
        team = team,
        analyses = analyses,
        insights = insights
    )
}
