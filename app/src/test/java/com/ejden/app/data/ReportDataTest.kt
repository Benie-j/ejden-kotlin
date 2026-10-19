package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val RD_ZONE = ZoneOffset.UTC
private val RD_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RD_PREV = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
private val RD_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

private val RD_PRODUCTS = listOf(
    Product("p1", "Riz", 600.0, 1000.0, 10, 5, null, null, "Alimentaire"),
    Product("p2", "Eau", 300.0, 500.0, 3, 5, null, null, "Boissons"),
    Product("p3", "Sel", 100.0, 200.0, 0, 2, null, null, null)
)

private val RD_CLIENTS = listOf(
    Client("c1", "Awa", "0101", "2026-10-02T08:00:00.000Z"),
    Client("c2", "Koffi", "0202", "2026-08-01T08:00:00.000Z"),
    Client("c3", "Mariam", "0303", "2026-07-01T08:00:00.000Z")
)

private fun rdItem(id: String, name: String, qty: Double, price: Double) =
    SaleItem(id, name, price, null, qty, qty * price)

private fun rdSale(
    id: String,
    createdAt: String,
    customer: String?,
    method: PaymentMethod,
    remaining: Double,
    cancelled: String?,
    vararg items: SaleItem
): Sale {
    val total = items.sumOf { it.total }
    return Sale(
        id, createdAt, items.toList(), total, 0.0, total, method,
        total - remaining, 0.0, remaining, customer, null, cancelled
    )
}

private val RD_SALES = listOf(
    rdSale("s0", "2026-09-10T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rdItem("p1", "Riz", 1.0, 1000.0)),
    rdSale("s1", "2026-10-05T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rdItem("p1", "Riz", 2.0, 1000.0)),
    rdSale("s2", "2026-10-06T12:00:00.000Z", "c2", PaymentMethod.Credit, 400.0, null,
        rdItem("p1", "Riz", 1.0, 1000.0)),
    rdSale("s3", "2026-10-07T12:00:00.000Z", null, PaymentMethod.Cash, 0.0, "2026-10-08T09:00:00.000Z",
        rdItem("p2", "Eau", 4.0, 500.0)),
    rdSale("s4", "2026-10-08T12:00:00.000Z", null, PaymentMethod.Cash, 0.0, null,
        rdItem("p2", "Eau", 1.0, 500.0))
)

private fun rdSource(sales: List<Sale> = RD_SALES) =
    ReportSource(sales, RD_PRODUCTS, RD_CLIENTS, emptyList(), emptyList())

class ReportDataTest {
    @Test
    fun fullReportBringsTheFourBlocksTogether() {
        val r = computeReport(rdSource(), ReportFilters(RD_RANGE), RD_PREV, RD_NOW, RD_ZONE)

        assertEquals(40, r.historyDays)
        assertEquals(3500.0, r.totals.revenue, 1e-9)
        assertEquals(3, r.totals.count)
        assertEquals(1, r.totals.cancelledCount)
        assertEquals(1000.0, r.previous!!.revenue, 1e-9)
        assertNull(r.previousNote)
        assertEquals(19, r.timeline.daily.size)

        // R1 : produits et stock
        assertEquals(listOf("p1", "p2"), r.products.rows.map { it.id })
        assertEquals(1.0, r.products.rows[0].prevQty!!, 1e-9)
        assertEquals(0.0, r.products.rows[1].prevQty!!, 1e-9)
        assertEquals(listOf("p3"), r.products.noSale.map { it.id })
        assertEquals(ReportStockStatus.Out, r.stock.rows.first { it.id == "p3" }.status)

        // R2 : clients et paiements
        assertEquals(listOf("c1", "c2", "c3"), r.clients.rows.map { it.id })
        assertEquals(2000.0, r.clients.rows[0].amount, 1e-9)
        assertEquals(400.0, r.payments.toReceive, 1e-9)
        assertEquals(3100.0, r.payments.collected, 1e-9)
        assertEquals(1, r.payments.creditSales)

        // Équipe
        assertEquals(1, r.team.users.size)
        assertEquals(3, r.team.users[0].count)
        assertEquals(3500.0, r.team.users[0].amount, 1e-9)
        assertEquals(1, r.team.users[0].cancelled)

        // R3 : trop peu de jours actifs pour une tendance
        assertEquals(true, r.analyses.trend is ReportAnalysis.Unavailable)

        // R4 : insights
        assertEquals(
            listOf(
                "Vos ventes progressent de " + ReportStats.formatPercent(250.0, false),
                "1 produit(s) en rupture de stock",
                "Les ventes à crédit représentent " + ReportStats.formatPercent(1000.0 / 3500.0 * 100, false) +
                    " de vos ventes, contre " + ReportStats.formatPercent(0.0, false) + " sur la période précédente"
            ),
            r.insights.map { it.title }
        )
    }

    @Test
    fun clientFilterNarrowsTheReportAndExplainsMissingComparison() {
        val filters = ReportFilters(RD_RANGE, clientId = "c2")
        val r = computeReport(rdSource(), filters, RD_PREV, RD_NOW, RD_ZONE)
        assertEquals(1000.0, r.totals.revenue, 1e-9)
        assertEquals(1, r.totals.count)
        assertNull(r.previous)
        assertEquals("Comparaison indisponible : aucune vente sur la période comparée.", r.previousNote)
        assertEquals(listOf("c2"), r.clients.rows.map { it.id })
    }

    @Test
    fun emptyDataGivesAnEmptyReport() {
        val r = computeReport(rdSource(emptyList()), ReportFilters(RD_RANGE), RD_PREV, RD_NOW, RD_ZONE)
        assertEquals(0, r.historyDays)
        assertEquals(0, r.totals.count)
        assertEquals(0.0, r.totals.revenue, 1e-9)
        assertNull(r.previous)
        assertEquals("Comparaison indisponible : aucune donnée n'existe avant cette période.", r.previousNote)
        assertEquals(emptyList<ReportInsight>(), r.insights)
        assertEquals(0, r.products.rows.size)
    }
}
