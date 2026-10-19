package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.sqrt

private val RA_START = LocalDate.of(2026, 9, 20)
private val RA_RANGE = ReportRange(RA_START, LocalDate.of(2026, 10, 19))

private fun raSeries(start: LocalDate, revenues: List<Double>): List<ReportDayPoint> =
    revenues.mapIndexed { i, revenue ->
        val d = start.plusDays(i.toLong())
        ReportDayPoint(d.toString(), d, "", revenue, if (revenue > 0) 1 else 0)
    }

private fun <T> ReportAnalysis<T>.ready(): T = when (this) {
    is ReportAnalysis.Ready -> value
    is ReportAnalysis.Unavailable -> throw AssertionError("analyse indisponible : $reason")
}

private fun raSale(id: String, createdAt: String, cancelled: String?, amount: Double): Sale {
    val item = SaleItem("p1", "Produit", amount, null, 1.0, amount)
    return Sale(
        id, createdAt, listOf(item), amount, 0.0, amount, PaymentMethod.Cash,
        amount, 0.0, 0.0, null, null, cancelled
    )
}

class ReportAnalysesTest {
    @Test
    fun everyAnalysisIsUnavailableWithShortHistory() {
        val a = computeReportAnalyses(raSeries(RA_START, List(10) { 1000.0 }), 10, RA_RANGE)
        assertEquals(
            ReportAnalysis.Unavailable(
                "EJDEN ne dispose pas encore de suffisamment d'historique " +
                    "(14 jours minimum, 10 actuellement) pour déterminer une tendance fiable."
            ),
            a.trend
        )
        assertTrue(a.variability is ReportAnalysis.Unavailable)
        assertTrue(a.anomalies is ReportAnalysis.Unavailable)
        assertTrue(a.seasonality is ReportAnalysis.Unavailable)
        assertTrue(a.forecast is ReportAnalysis.Unavailable)
    }

    @Test
    fun steadyGrowthGivesTrendVariabilityAndForecast() {
        val revenues = List(30) { 1000.0 + 100.0 * it }
        val a = computeReportAnalyses(raSeries(RA_START, revenues), 30, RA_RANGE)
        val sd = sqrt(775000.0)

        val t = a.trend.ready()
        assertEquals(100.0, t.slopePerDay, 1e-9)
        assertEquals(29 * 100.0 / 2450.0 * 100, t.relative, 1e-9)
        assertEquals(1.0, t.r2, 1e-9)
        assertEquals(ReportDirection.Rise, t.direction)
        assertEquals(ReportAcceleration.Flat, t.acceleration)
        assertNull(t.ma7[5])
        assertEquals(1300.0, t.ma7[6]!!, 1e-9)
        assertEquals(ReportConfidence.Medium, t.confidence)

        val v = a.variability.ready()
        assertEquals(2450.0, v.mean, 1e-9)
        assertEquals(2450.0, v.median, 1e-9)
        assertEquals(775000.0, v.variance, 1e-6)
        assertEquals(sd, v.std, 1e-9)
        assertEquals(sd / 2450.0, v.cv!!, 1e-9)

        val an = a.anomalies.ready()
        assertEquals(0, an.items.size)
        assertEquals(2450.0 - sd, an.low, 1e-9)
        assertEquals(2450.0 + sd, an.high, 1e-9)

        val f = a.forecast.ready()
        assertEquals(7, f.nextDays)
        assertEquals(22750.0, f.expectedRevenue, 1e-9)
        assertEquals(22750.0 - sd * sqrt(7.0), f.low, 1e-6)
        assertEquals(22750.0 + sd * sqrt(7.0), f.high, 1e-6)
        assertEquals(ReportConfidence.Medium, f.confidence)
    }

    @Test
    fun isolatedSpikeIsFlaggedOnlyInsideTheDisplayedPeriod() {
        val revenues = List(30) { i -> if (i == 20) 5000.0 else if (i % 2 == 0) 900.0 else 1100.0 }
        val series = raSeries(RA_START, revenues)
        val an = computeReportAnalyses(series, 30, RA_RANGE).anomalies.ready()
        assertEquals(1, an.items.size)
        assertEquals(reportDay(LocalDate.of(2026, 10, 10)), an.items[0].day)
        assertEquals(ReportAnomalyKind.High, an.items[0].kind)
        assertTrue(an.items[0].z > 2.5)

        val narrow = ReportRange(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 19))
        assertEquals(0, computeReportAnalyses(series, 30, narrow).anomalies.ready().items.size)
    }

    @Test
    fun weekdaySeasonalityIndex() {
        val start = LocalDate.of(2026, 9, 21)
        val revenues = List(28) { i -> if (start.plusDays(i.toLong()).dayOfWeek.value <= 5) 1000.0 else 0.0 }
        val range = ReportRange(start, start.plusDays(27))
        val s = computeReportAnalyses(raSeries(start, revenues), 28, range).seasonality.ready()
        assertEquals(
            listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi"),
            s.weekday.map { it.label }
        )
        val byLabel = s.weekday.associate { it.label to it.index }
        assertEquals(40.0, byLabel.getValue("Lundi"), 1e-9)
        assertEquals(-100.0, byLabel.getValue("Samedi"), 1e-9)
        assertEquals(-100.0, byLabel.getValue("Dimanche"), 1e-9)
        assertEquals(ReportConfidence.Medium, s.confidence)
        assertEquals(
            "Saisonnalité mensuelle indisponible : 12 mois d'historique sont nécessaires.",
            s.monthNote
        )
    }

    @Test
    fun historySeriesIgnoresCancelledSalesAndStatusFilter() {
        val sales = listOf(
            raSale("x1", "2026-10-17T12:00:00.000Z", null, 1000.0),
            raSale("x2", "2026-10-18T12:00:00.000Z", "2026-10-18T15:00:00.000Z", 700.0),
            raSale("x3", "2026-10-10T12:00:00.000Z", null, 400.0)
        )
        val filters = ReportFilters(RA_RANGE, status = ReportStatus.Cancelled)
        val series = buildReportHistorySeries(
            sales, filters, emptyMap(), 5, LocalDate.of(2026, 10, 19), ZoneOffset.UTC
        )
        assertEquals((15..19).map { LocalDate.of(2026, 10, it) }, series.map { it.date })
        assertEquals(listOf(0.0, 0.0, 1000.0, 0.0, 0.0), series.map { it.revenue })
    }
}
