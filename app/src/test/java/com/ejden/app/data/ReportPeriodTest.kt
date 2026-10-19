package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun rp(y: Int, m: Int, d: Int): LocalDate = LocalDate.of(y, m, d)
private fun rr(a: LocalDate, b: LocalDate) = ReportRange(a, b)

class ReportPeriodTest {
    private val monday = rp(2026, 10, 19)
    private val wednesday = rp(2026, 10, 21)

    @Test fun periodesSimples() {
        assertEquals(rr(monday, monday), resolveReportPeriod(ReportPeriod.Today, null, monday))
        assertEquals(rr(rp(2026, 10, 18), rp(2026, 10, 18)), resolveReportPeriod(ReportPeriod.Yesterday, null, monday))
        assertEquals(rr(rp(2026, 10, 1), monday), resolveReportPeriod(ReportPeriod.Month, null, monday))
        assertEquals(rr(rp(2026, 9, 1), rp(2026, 9, 30)), resolveReportPeriod(ReportPeriod.LastMonth, null, monday))
        assertEquals(rr(rp(2026, 1, 1), monday), resolveReportPeriod(ReportPeriod.Year, null, monday))
        assertEquals(rr(rp(2025, 1, 1), rp(2025, 12, 31)), resolveReportPeriod(ReportPeriod.LastYear, null, monday))
    }

    @Test fun semaines() {
        assertEquals(rr(monday, wednesday), resolveReportPeriod(ReportPeriod.Week, null, wednesday))
        assertEquals(rr(rp(2026, 10, 12), rp(2026, 10, 18)), resolveReportPeriod(ReportPeriod.LastWeek, null, wednesday))
        assertEquals(rr(monday, monday), resolveReportPeriod(ReportPeriod.Week, null, monday))
    }

    @Test fun joursGlissants() {
        assertEquals(rr(rp(2026, 10, 13), monday), resolveReportPeriod(ReportPeriod.Days7, null, monday))
        assertEquals(rr(rp(2026, 9, 20), monday), resolveReportPeriod(ReportPeriod.Days30, null, monday))
        assertEquals(rr(rp(2026, 7, 22), monday), resolveReportPeriod(ReportPeriod.Days90, null, monday))
    }

    @Test fun personnalisee() {
        val reversed = rr(rp(2026, 10, 10), rp(2026, 10, 5))
        assertEquals(rr(rp(2026, 10, 5), rp(2026, 10, 10)), resolveReportPeriod(ReportPeriod.Custom, reversed, monday))
        assertEquals(rr(monday, monday), resolveReportPeriod(ReportPeriod.Custom, null, monday))
    }

    @Test fun nombreDeJoursEtLabels() {
        assertEquals(19, rr(rp(2026, 10, 1), monday).dayCount)
        assertEquals(1, rr(monday, monday).dayCount)
        assertEquals("19/10/2026", reportRangeLabel(rr(monday, monday)))
        assertEquals("01/10/2026 → 19/10/2026", reportRangeLabel(rr(rp(2026, 10, 1), monday)))
    }

    @Test fun bornesEnMillisecondes() {
        val range = rr(rp(2026, 10, 1), monday)
        val z = ZoneOffset.UTC
        assertTrue(range.contains(Instant.parse("2026-10-19T23:59:59Z").toEpochMilli(), z))
        assertFalse(range.contains(Instant.parse("2026-10-20T00:00:00Z").toEpochMilli(), z))
        assertFalse(range.contains(Instant.parse("2026-09-30T23:59:59Z").toEpochMilli(), z))
        assertTrue(range.contains(Instant.parse("2026-10-01T00:00:00Z").toEpochMilli(), z))
    }

    @Test fun comparaisons() {
        val month = rr(rp(2026, 10, 1), monday)
        assertNull(resolveReportComparison(ReportCompare.None, ReportPeriod.Month, month))
        assertEquals(
            rr(rp(2026, 9, 1), rp(2026, 9, 19)),
            resolveReportComparison(ReportCompare.Previous, ReportPeriod.Month, month)
        )
        assertEquals(
            rr(rp(2025, 10, 1), rp(2025, 10, 19)),
            resolveReportComparison(ReportCompare.LastYear, ReportPeriod.Month, month)
        )
        val lastMonth = rr(rp(2026, 9, 1), rp(2026, 9, 30))
        assertEquals(
            rr(rp(2026, 8, 1), rp(2026, 8, 31)),
            resolveReportComparison(ReportCompare.Previous, ReportPeriod.LastMonth, lastMonth)
        )
        val days7 = rr(rp(2026, 10, 13), monday)
        val before = rr(rp(2026, 10, 6), rp(2026, 10, 12))
        assertEquals(before, resolveReportComparison(ReportCompare.Duration, ReportPeriod.Days7, days7))
        assertEquals(before, resolveReportComparison(ReportCompare.Previous, ReportPeriod.Days7, days7))
        assertEquals(
            rr(rp(2025, 1, 1), rp(2025, 10, 19)),
            resolveReportComparison(ReportCompare.Previous, ReportPeriod.Year, rr(rp(2026, 1, 1), monday))
        )
    }

    @Test fun moisPlusCourtEtAnneeBissextile() {
        val march = rr(rp(2026, 3, 1), rp(2026, 3, 31))
        assertEquals(
            rr(rp(2026, 2, 1), rp(2026, 2, 28)),
            resolveReportComparison(ReportCompare.Previous, ReportPeriod.Month, march)
        )
        // Comme JavaScript : le 29/02/2024 un an avant donne le 01/03/2023.
        val leap = rr(rp(2024, 2, 29), rp(2024, 3, 5))
        assertEquals(
            rr(rp(2023, 3, 1), rp(2023, 3, 5)),
            resolveReportComparison(ReportCompare.LastYear, ReportPeriod.Custom, leap)
        )
    }

    @Test fun comparaisonPersonnalisee() {
        val given = rr(rp(2026, 5, 1), rp(2026, 5, 10))
        val current = rr(rp(2026, 10, 1), monday)
        assertEquals(given, resolveReportComparison(ReportCompare.Custom, ReportPeriod.Month, current, given))
        assertNull(resolveReportComparison(ReportCompare.Custom, ReportPeriod.Month, current, null))
    }
}
