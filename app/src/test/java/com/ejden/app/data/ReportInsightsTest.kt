package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

private val RI_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RI_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
private const val RI_DAY = 86_400_000L

private fun riTotals(revenue: Double, count: Int) = ReportTotals(
    revenue, count, count.toDouble(), if (count > 0) revenue / count else 0.0, 0, 0.0, null
)

private val RI_NONE = ReportAnalysis.Unavailable("indisponible")

private fun riAnalyses(
    trend: ReportAnalysis<ReportTrendResult> = RI_NONE,
    anomalies: ReportAnalysis<ReportAnomalies> = RI_NONE
) = ReportAnalyses(trend, RI_NONE, anomalies, RI_NONE, RI_NONE)

private fun riStockRow(
    id: String,
    name: String,
    stock: Int,
    daysLeft: Double?,
    rate: Double?,
    status: ReportStockStatus
) = ReportStockRow(id, name, stock, 2, 0.0, 0.0, rate, daysLeft, status, null)

private fun riStock(rows: List<ReportStockRow>) = ReportStock(rows, 0.0, 0, 0, emptyList(), null)

private fun riPayments(
    creditShare: Double = 0.0,
    previous: Double? = null,
    creditSales: Int = 0,
    overdue: Double = 0.0,
    overdueCount: Int = 0
) = ReportPayments(
    emptyList(), 0.0, 0.0, creditSales, 0.0, 0.0, overdue, overdueCount, emptyList(), creditShare, previous
)

private fun riClientRow(id: String, count: Int, last: Long?, interval: Double?) =
    ReportClientRow(id, id, count, 0.0, 0.0, last, interval, false, ReportClientStatus.Inactive)

private fun riClients(rows: List<ReportClientRow>) = ReportClients(rows, rows.size, 0, 0, 0, 0, 0.0)

private fun riBuild(
    totals: ReportTotals = riTotals(1000.0, 10),
    previous: ReportTotals? = null,
    analyses: ReportAnalyses = riAnalyses(),
    stock: ReportStock = riStock(emptyList()),
    payments: ReportPayments = riPayments(),
    clients: ReportClients = riClients(emptyList())
): List<ReportInsight> =
    buildReportInsights(RI_RANGE, 30, totals, previous, analyses, stock, payments, clients, RI_NOW)

class ReportInsightsTest {
    @Test
    fun noSalesMeansNoInsights() {
        val out = riBuild(
            totals = riTotals(0.0, 0),
            stock = riStock(listOf(riStockRow("s1", "Riz", 0, null, null, ReportStockStatus.Out))),
            payments = riPayments(overdueCount = 2, overdue = 500.0)
        )
        assertEquals(emptyList<ReportInsight>(), out)
    }

    @Test
    fun salesChangeNeedsAtLeastFivePercent() {
        val up = riBuild(totals = riTotals(1200.0, 12), previous = riTotals(1000.0, 10)).single()
        assertEquals("Ventes", up.type)
        assertEquals(ReportInsightLevel.Positive, up.level)
        assertEquals("Vos ventes progressent de " + ReportStats.formatPercent(20.0, false), up.title)
        assertEquals("12 ventes, 10 ventes sur la période comparée", up.data)
        assertEquals(ReportConfidence.Medium, up.confidence)
        assertEquals(reportRangeLabel(RI_RANGE), up.period)

        val down = riBuild(totals = riTotals(800.0, 8), previous = riTotals(1000.0, 10)).single()
        assertEquals(ReportInsightLevel.Warning, down.level)
        assertEquals("Vos ventes reculent de " + ReportStats.formatPercent(20.0, false), down.title)

        assertEquals(0, riBuild(totals = riTotals(1030.0, 10), previous = riTotals(1000.0, 10)).size)
    }

    @Test
    fun trendInsightOnlyWhenNotFlat() {
        val rising = ReportTrendResult(
            100.0, 25.0, 0.9876, ReportDirection.Rise, ReportAcceleration.Speeding,
            emptyList(), ReportConfidence.High
        )
        val i = riBuild(analyses = riAnalyses(trend = ReportAnalysis.Ready(rising))).single()
        assertEquals("Tendance des ventes à la hausse", i.title)
        assertEquals(ReportInsightLevel.Positive, i.level)
        assertEquals(
            "Pente estimée sur 30 jours (" + ReportStats.formatPercent(25.0) + " sur l'historique, accélération).",
            i.explanation
        )
        assertEquals("30 derniers jours", i.period)
        assertEquals("Régression linéaire, R² = 0.99", i.data)
        assertEquals(ReportConfidence.High, i.confidence)

        val flat = rising.copy(direction = ReportDirection.Flat)
        assertEquals(0, riBuild(analyses = riAnalyses(trend = ReportAnalysis.Ready(flat))).size)
    }

    @Test
    fun onlyTheFirstTwoAnomaliesAreReported() {
        val items = listOf(
            ReportAnomalyItem("10/10/2026", 5000.0, 3.456, ReportAnomalyKind.High),
            ReportAnomalyItem("12/10/2026", 0.0, -2.9, ReportAnomalyKind.Low),
            ReportAnomalyItem("14/10/2026", 6000.0, 4.0, ReportAnomalyKind.High)
        )
        val anomalies = ReportAnomalies(500.0, 1500.0, items, null, ReportConfidence.Medium)
        val out = riBuild(analyses = riAnalyses(anomalies = ReportAnalysis.Ready(anomalies)))
        assertEquals(2, out.size)
        assertEquals("Activité exceptionnellement élevée le 10/10/2026", out[0].title)
        assertEquals(
            statNumber(5000.0) + " FCFA, alors que l'activité habituelle se situe entre " +
                statNumber(500.0) + " et " + statNumber(1500.0) + " FCFA.",
            out[0].explanation
        )
        assertEquals("Z-score = 3.5", out[0].data)
        assertEquals("Activité exceptionnellement faible le 12/10/2026", out[1].title)
    }

    @Test
    fun urgentStockThenOutOfStock() {
        val rows = listOf(
            riStockRow("a", "Riz", 5, 2.4, 2.0, ReportStockStatus.Low),
            riStockRow("b", "Eau", 12, 6.0, 2.0, ReportStockStatus.Ok),
            riStockRow("c", "Huile", 30, 9.0, 3.0, ReportStockStatus.Ok),
            riStockRow("d", "Lait", 0, 1.0, 4.0, ReportStockStatus.Out),
            riStockRow("e", "Sel", 1, 0.2, 5.0, ReportStockStatus.Low)
        )
        val out = riBuild(stock = riStock(rows))
        assertEquals(
            listOf(
                "Le stock de « Sel » pourrait être insuffisant dans environ 1 jour(s)",
                "Le stock de « Riz » pourrait être insuffisant dans environ 2 jour(s)",
                "Le stock de « Eau » pourrait être insuffisant dans environ 6 jour(s)",
                "1 produit(s) en rupture de stock"
            ),
            out.map { it.title }
        )
        assertEquals(listOf("i1", "i2", "i3", "i4"), out.map { it.id })
        assertEquals("Stock 1, vente moyenne 5/jour.", out[0].explanation)
        assertEquals(ReportInsightLevel.Alert, out[0].level)
        assertEquals(ReportInsightLevel.Alert, out[1].level)
        assertEquals(ReportInsightLevel.Warning, out[2].level)
        assertEquals(ReportConfidence.High, out[0].confidence)
        assertEquals(null, out[3].confidence)
        assertEquals("Aujourd'hui", out[3].period)
    }

    @Test
    fun creditShareAndOverdueCredits() {
        val out = riBuild(
            payments = riPayments(creditShare = 40.0, previous = 20.0, creditSales = 3, overdue = 800.0, overdueCount = 2)
        )
        assertEquals(2, out.size)
        assertEquals(
            "Les ventes à crédit représentent " + ReportStats.formatPercent(40.0, false) +
                " de vos ventes, contre " + ReportStats.formatPercent(20.0, false) + " sur la période précédente",
            out[0].title
        )
        assertEquals(ReportInsightLevel.Warning, out[0].level)
        assertEquals("3 vente(s) à crédit", out[0].data)
        assertEquals("2 crédit(s) en retard", out[1].title)
        assertEquals(
            statNumber(800.0) + " FCFA restent à encaisser depuis plus de 30 jours.",
            out[1].explanation
        )
        assertEquals(ReportInsightLevel.Alert, out[1].level)

        val better = riBuild(payments = riPayments(creditShare = 10.0, previous = 20.0)).single()
        assertEquals(ReportInsightLevel.Positive, better.level)
        assertEquals(0, riBuild(payments = riPayments(creditShare = 22.0, previous = 20.0)).size)
    }

    @Test
    fun silentClientsAreCountedOnlyPastTwiceTheirUsualInterval() {
        val rows = listOf(
            riClientRow("late", 0, RI_NOW - 25 * RI_DAY, 10.0),
            riClientRow("ok", 0, RI_NOW - 15 * RI_DAY, 10.0),
            riClientRow("buyer", 1, RI_NOW - 25 * RI_DAY, 10.0),
            riClientRow("unknown", 0, RI_NOW - 90 * RI_DAY, null)
        )
        val i = riBuild(clients = riClients(rows)).single()
        assertEquals(
            "1 client(s) n'ont effectué aucun achat depuis plus de 2 fois leur fréquence habituelle",
            i.title
        )
        assertEquals(ReportInsightLevel.Info, i.level)
        assertEquals(ReportConfidence.Medium, i.confidence)
        assertTrue(i.data.startsWith("Intervalle"))
    }
}
