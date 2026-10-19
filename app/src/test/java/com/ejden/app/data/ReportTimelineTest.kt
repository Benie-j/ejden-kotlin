package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val RT_ZONE = ZoneOffset.UTC

private fun rtSale(id: String, createdAt: String, total: Double, cancelled: String? = null) = Sale(
    id, createdAt, listOf(SaleItem("p", "Produit", total, null, 1.0, total)),
    total, 0.0, total, PaymentMethod.Cash, total, 0.0, 0.0, null, null, cancelled
)

private fun rtViews(sales: List<Sale>, range: ReportRange) =
    buildSaleViews(sales, ReportFilters(range), range, emptyMap(), RT_ZONE)

private val RT_WEEK = ReportRange(LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 11))

private val RT_SALES = listOf(
    rtSale("a", "2026-10-05T09:30:00.000Z", 1000.0),
    rtSale("b", "2026-10-05T14:10:00.000Z", 500.0),
    rtSale("c", "2026-10-07T09:45:00.000Z", 2000.0),
    rtSale("d", "2026-10-11T18:00:00.000Z", 300.0),
    rtSale("e", "2026-10-06T10:00:00.000Z", 700.0, cancelled = "2026-10-06T11:00:00.000Z"),
    rtSale("f", "2026-10-04T23:59:00.000Z", 999.0)
)

class ReportTimelineTest {

    @Test fun serieParJour() {
        val daily = reportDailySeries(rtViews(RT_SALES, RT_WEEK), RT_WEEK, RT_ZONE)
        assertEquals(7, daily.size)
        assertEquals("2026-10-05", daily[0].day)
        assertEquals("05/10", daily[0].label)
        assertEquals(1500.0, daily[0].revenue, 1e-9)
        assertEquals(2, daily[0].count)
        assertEquals(0.0, daily[1].revenue, 1e-9)
        assertEquals(0, daily[1].count)
        assertEquals(2000.0, daily[2].revenue, 1e-9)
        assertEquals(300.0, daily[6].revenue, 1e-9)
    }

    @Test fun uneSeuleSemaine() {
        val t = buildReportTimeline(rtViews(RT_SALES, RT_WEEK), RT_WEEK, RT_ZONE)
        assertEquals(1, t.weekly.size)
        assertEquals("Sem. 05/10", t.weekly[0].label)
        assertEquals(3800.0, t.weekly[0].revenue, 1e-9)
        assertEquals(4, t.weekly[0].count)
        assertEquals(1, t.monthly.size)
        assertEquals("oct. 2026", t.monthly[0].label)
    }

    @Test fun deuxSemainesEtDeuxMois() {
        val range = ReportRange(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 6))
        val sales = listOf(
            rtSale("g", "2026-09-30T10:00:00.000Z", 1000.0),
            rtSale("h", "2026-10-01T10:00:00.000Z", 200.0),
            rtSale("i", "2026-10-06T10:00:00.000Z", 100.0)
        )
        val t = buildReportTimeline(rtViews(sales, range), range, RT_ZONE)
        assertEquals(listOf("Sem. 28/09", "Sem. 05/10"), t.weekly.map { it.label })
        assertEquals(1200.0, t.weekly[0].revenue, 1e-9)
        assertEquals(2, t.weekly[0].count)
        assertEquals(100.0, t.weekly[1].revenue, 1e-9)
        assertEquals(listOf("sept. 2026", "oct. 2026"), t.monthly.map { it.label })
        assertEquals(1000.0, t.monthly[0].revenue, 1e-9)
        assertEquals(300.0, t.monthly[1].revenue, 1e-9)
        assertEquals(2, t.monthly[1].count)
    }

    @Test fun jourDeLaSemaine() {
        val t = buildReportTimeline(rtViews(RT_SALES, RT_WEEK), RT_WEEK, RT_ZONE)
        assertEquals(7, t.weekdays.size)
        assertEquals("Dimanche", t.weekdays[0].label)
        assertEquals(300.0, t.weekdays[0].revenue, 1e-9)
        assertEquals("Lundi", t.weekdays[1].label)
        assertEquals(1500.0, t.weekdays[1].avgPerDay, 1e-9)
        assertEquals(2, t.weekdays[1].count)
        assertEquals("Mercredi", t.weekdays[3].label)
        assertEquals(2000.0, t.weekdays[3].revenue, 1e-9)
        assertEquals(0.0, t.weekdays[2].avgPerDay, 1e-9)
    }

    @Test fun parHeure() {
        val t = buildReportTimeline(rtViews(RT_SALES, RT_WEEK), RT_WEEK, RT_ZONE)
        assertEquals(24, t.hours.size)
        assertEquals(2, t.hours[9].count)
        assertEquals(3000.0, t.hours[9].revenue, 1e-9)
        assertEquals(1, t.hours[14].count)
        assertEquals(500.0, t.hours[14].revenue, 1e-9)
        assertEquals(300.0, t.hours[18].revenue, 1e-9)
        assertEquals(0, t.hours[3].count)
    }

    private val history = listOf(
        rtSale("old", "2026-09-20T12:00:00.000Z", 800.0),
        rtSale("older-cancelled", "2026-09-01T12:00:00.000Z", 50.0, cancelled = "2026-09-02T12:00:00.000Z"),
        rtSale("bad", "pas une date", 10.0)
    )

    @Test fun historique() {
        val h = computeReportHistory(history, LocalDate.of(2026, 10, 19), RT_ZONE)
        assertNotNull(h.firstAt)
        assertEquals(30, h.days)
        val none = computeReportHistory(emptyList(), LocalDate.of(2026, 10, 19), RT_ZONE)
        assertNull(none.firstAt)
        assertEquals(0, none.days)
    }

    @Test fun comparaisonDisponible() {
        val firstAt = computeReportHistory(history, LocalDate.of(2026, 10, 19), RT_ZONE).firstAt
        val range = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        val c = computeReportComparison(history, ReportFilters(range), range, emptyMap(), firstAt, RT_ZONE)
        assertNull(c.note)
        assertEquals(800.0, c.previous!!.revenue, 1e-9)
    }

    @Test fun comparaisonIndisponible() {
        val firstAt = computeReportHistory(history, LocalDate.of(2026, 10, 19), RT_ZONE).firstAt
        val filters = ReportFilters(RT_WEEK)

        assertNull(computeReportComparison(history, filters, null, emptyMap(), firstAt, RT_ZONE).note)

        val before = ReportRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
        assertEquals(
            "Comparaison indisponible : aucune donnée n'existe avant cette période.",
            computeReportComparison(history, filters, before, emptyMap(), firstAt, RT_ZONE).note
        )

        val empty = ReportRange(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 30))
        val c = computeReportComparison(history, filters, empty, emptyMap(), firstAt, RT_ZONE)
        assertEquals("Comparaison indisponible : aucune vente sur la période comparée.", c.note)
        assertNull(c.previous)

        assertEquals(
            "Comparaison indisponible : aucune donnée n'existe avant cette période.",
            computeReportComparison(emptyList(), filters, empty, emptyMap(), null, RT_ZONE).note
        )
    }
}
