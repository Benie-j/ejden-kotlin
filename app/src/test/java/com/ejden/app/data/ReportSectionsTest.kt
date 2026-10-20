package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val RS_ZONE = ZoneOffset.UTC
private val RS_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RS_PREV = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
private val RS_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

private val RS_PRODUCTS = listOf(
    Product("p1", "Riz", 600.0, 1000.0, 10, 5, null, null, "Alimentaire"),
    Product("p2", "Eau", 300.0, 500.0, 3, 5, null, null, "Boissons"),
    Product("p3", "Sel", 100.0, 200.0, 0, 2, null, null, null)
)

private val RS_CLIENTS = listOf(
    Client("c1", "Awa", "0101", "2026-10-02T08:00:00.000Z"),
    Client("c2", "Koffi", "0202", "2026-08-01T08:00:00.000Z")
)

private fun rsItem(id: String, name: String, qty: Double, price: Double) =
    SaleItem(id, name, price, null, qty, qty * price)

private fun rsSale(
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

private val RS_SALES = listOf(
    rsSale("s0", "2026-09-10T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rsItem("p1", "Riz", 1.0, 1000.0)),
    rsSale("s1", "2026-10-05T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rsItem("p1", "Riz", 2.0, 1000.0)),
    rsSale("s2", "2026-10-06T12:00:00.000Z", "c2", PaymentMethod.Credit, 400.0, null,
        rsItem("p1", "Riz", 1.0, 1000.0)),
    rsSale("s3", "2026-10-07T12:00:00.000Z", null, PaymentMethod.Cash, 0.0, "2026-10-08T09:00:00.000Z",
        rsItem("p2", "Eau", 4.0, 500.0)),
    rsSale("s4", "2026-10-08T12:00:00.000Z", null, PaymentMethod.Cash, 0.0, null,
        rsItem("p2", "Eau", 1.0, 500.0))
)

private fun rsReport(compare: ReportRange? = RS_PREV): ReportData =
    computeReport(
        ReportSource(RS_SALES, RS_PRODUCTS, RS_CLIENTS, emptyList(), emptyList()),
        ReportFilters(RS_RANGE), compare, RS_NOW, RS_ZONE
    )

private fun ReportSection.table(id: String): ReportTable = tables.first { it.id == id }

class ReportSectionsTest {
    @Test
    fun salesKpisCarryDeltasAgainstThePreviousPeriod() {
        val s = buildSalesSection(rsReport(), ReportPermissions())
        assertEquals(
            listOf("revenue", "count", "units", "average", "cancelled", "margin"),
            s.kpis.map { it.id }
        )
        val k = s.kpis.associateBy { it.id }
        assertEquals(statNumber(3500.0) + " FCFA", k.getValue("revenue").value)
        assertEquals("↑ " + ReportStats.formatPercent(250.0), k.getValue("revenue").delta)
        assertEquals(ReportTone.Up, k.getValue("revenue").tone)
        assertEquals("3", k.getValue("count").value)
        assertEquals("↑ " + ReportStats.formatPercent(200.0), k.getValue("count").delta)
        assertEquals(statNumber(4.0, 1), k.getValue("units").value)
        assertEquals("↑ " + ReportStats.formatPercent(300.0), k.getValue("units").delta)
        assertEquals(statNumber(1167.0) + " FCFA", k.getValue("average").value)
        assertEquals(
            "↑ " + ReportStats.formatPercent((3500.0 / 3 - 1000.0) / 1000.0 * 100),
            k.getValue("average").delta
        )
        assertEquals("1 (" + statNumber(2000.0) + " FCFA)", k.getValue("cancelled").value)
        assertNull(k.getValue("cancelled").delta)
        assertEquals("—", k.getValue("margin").value)
    }

    @Test
    fun marginIsHiddenWithoutFinancialPermissionAndDeltasWithoutComparison() {
        val noFinance = buildSalesSection(rsReport(), ReportPermissions(viewFinancialReports = false))
        assertEquals(
            listOf("revenue", "count", "units", "average", "cancelled"),
            noFinance.kpis.map { it.id }
        )
        val noCompare = buildSalesSection(rsReport(compare = null), ReportPermissions())
        assertNull(noCompare.kpis.first { it.id == "revenue" }.delta)
        assertNull(noCompare.kpis.first { it.id == "revenue" }.tone)
    }

    @Test
    fun salesChartAndTables() {
        val r = rsReport()
        val s = buildSalesSection(r, ReportPermissions())
        val chart = s.chart!!
        assertEquals("Chiffre d'affaires par jour", chart.title)
        assertEquals(19, chart.labels.size)
        assertEquals(2000.0, chart.values[4], 1e-9)
        assertEquals(3500.0, chart.values.sum(), 1e-9)

        assertEquals(
            listOf(
                listOf<Any>("05/10/2026", 1L, 2000L),
                listOf<Any>("06/10/2026", 1L, 1000L),
                listOf<Any>("08/10/2026", 1L, 500L)
            ),
            s.table("by-day").rows
        )
        val week = r.timeline.weekly.first { it.count > 0 }
        assertEquals(listOf(listOf<Any>(week.label, 3L, 3500L)), s.table("by-week").rows)
        val month = r.timeline.monthly.first { it.count > 0 }
        assertEquals(listOf(listOf<Any>(month.label, 3L, 3500L)), s.table("by-month").rows)

        val weekdays = s.table("by-weekday").rows
        assertEquals(listOf("Lundi", "Mardi", "Jeudi"), weekdays.map { it[0] })
        assertEquals(listOf<Any>(1L, 1L, 1L), weekdays.map { it[1] })
        assertEquals(listOf<Any>(2000L, 1000L, 500L), weekdays.map { it[2] })
        val monday = r.timeline.weekdays.first { it.label == "Lundi" }
        assertEquals(Math.round(monday.avgPerDay), weekdays[0][3])

        assertEquals(listOf(listOf<Any>("12h", 3L, 3500L)), s.table("by-hour").rows)
    }

    @Test
    fun productsSection() {
        val s = buildProductsSection(rsReport())
        assertEquals(listOf("2", "1"), s.kpis.map { it.value })
        assertEquals(listOf("top", "bottom", "rising", "falling", "nosale"), s.tables.map { it.id })
        assertEquals(
            listOf(
                listOf<Any>("Riz", "Alimentaire", 3L, 3000L, 85.7, 10.5),
                listOf<Any>("Eau", "Boissons", 1L, 500L, 14.3, 5.3)
            ),
            s.table("top").rows
        )
        assertEquals(listOf("Eau", "Riz"), s.table("bottom").rows.map { it[0] })
        assertEquals(listOf(listOf<Any>("Riz", 3L, 1L)), s.table("rising").rows)
        assertEquals(emptyList<List<Any>>(), s.table("falling").rows)
        assertEquals(listOf(listOf<Any>("Sel", 0L)), s.table("nosale").rows)
        assertEquals(
            "Classification ABC indisponible : au moins 10 produits vendus sont nécessaires.",
            s.message
        )
    }

    @Test
    fun productsSectionWithoutComparisonHasNoRisingOrFallingTables() {
        val s = buildProductsSection(rsReport(compare = null))
        assertEquals(listOf("top", "bottom", "nosale"), s.tables.map { it.id })
    }

    @Test
    fun stockSection() {
        val s = buildStockSection(rsReport())
        val k = s.kpis.associateBy { it.id }
        assertEquals(statNumber(6900.0) + " FCFA", k.getValue("s-value").value)
        assertEquals("0", k.getValue("s-in").value)
        assertEquals("0", k.getValue("s-out").value)
        assertEquals("1", k.getValue("s-rupt").value)
        assertEquals("1", k.getValue("s-low").value)
        assertEquals(statNumber((0.3 + 1.0 / 3.0) / 2, 2) + "×", k.getValue("s-rot").value)

        assertEquals(
            listOf(
                listOf<Any>("Riz", 10L, 5L, 6000L, 3L, "OK"),
                listOf<Any>("Eau", 3L, 5L, 900L, 1L, "Faible"),
                listOf<Any>("Sel", 0L, 2L, 0L, 0L, "Rupture")
            ),
            s.table("levels").rows
        )
        assertEquals(
            listOf(
                listOf<Any>("Eau", 3L, 0.05, 57L, "Moyenne"),
                listOf<Any>("Riz", 10L, 0.16, 63.3, "Moyenne")
            ),
            s.table("risk").rows
        )
        assertEquals(
            "Estimation fondée sur le rythme de vente de la période ; " +
                "elle ne tient pas compte des réapprovisionnements futurs.",
            s.table("risk").note
        )
        assertEquals(emptyList<List<Any>>(), s.table("lowrot").rows)
    }

    @Test
    fun permissionsGateSections() {
        val all = ReportPermissions()
        assertEquals(true, ReportSectionId.values().all { reportPermissionFor(it, all) })
        val limited = ReportPermissions(viewFinancialReports = false, viewTeamReports = false)
        assertEquals(false, reportPermissionFor(ReportSectionId.Payments, limited))
        assertEquals(false, reportPermissionFor(ReportSectionId.Credits, limited))
        assertEquals(false, reportPermissionFor(ReportSectionId.Team, limited))
        assertEquals(true, reportPermissionFor(ReportSectionId.Analyses, limited))
        assertEquals(true, reportPermissionFor(ReportSectionId.Sales, limited))
        val none = ReportPermissions(viewReports = false)
        assertEquals(false, reportPermissionFor(ReportSectionId.Sales, none))
    }
}
