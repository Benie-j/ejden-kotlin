package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReportStatsTest {
    private val sample = listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0)

    @Test fun moyenneEtMediane() {
        assertNull(ReportStats.mean(emptyList()))
        assertEquals(5.0, ReportStats.mean(sample)!!, 1e-9)
        assertEquals(2.0, ReportStats.median(listOf(3.0, 1.0, 2.0))!!, 1e-9)
        assertEquals(2.5, ReportStats.median(listOf(4.0, 1.0, 3.0, 2.0))!!, 1e-9)
        assertNull(ReportStats.median(emptyList()))
    }

    @Test fun varianceEcartTypeEtZScore() {
        assertEquals(32.0 / 7, ReportStats.variance(sample)!!, 1e-9)
        assertEquals(Math.sqrt(32.0 / 7), ReportStats.stdDev(sample)!!, 1e-9)
        assertNull(ReportStats.variance(listOf(1.0)))
        assertEquals(4.0 / Math.sqrt(32.0 / 7), ReportStats.zScore(9.0, sample)!!, 1e-9)
        assertNull(ReportStats.zScore(3.0, listOf(2.0, 2.0, 2.0)))
        assertEquals(Math.sqrt(32.0 / 7) / 5.0, ReportStats.coefVariation(sample)!!, 1e-9)
        assertNull(ReportStats.coefVariation(listOf(-1.0, 1.0)))
    }

    @Test fun moyenneMobile() {
        assertEquals(
            listOf(null, 1.5, 2.5, 3.5),
            ReportStats.movingAverage(listOf(1.0, 2.0, 3.0, 4.0), 2)
        )
    }

    @Test fun tendanceLineaire() {
        val t = ReportStats.linearTrend(listOf(1.0, 2.0, 3.0, 4.0))!!
        assertEquals(1.0, t.slope, 1e-9)
        assertEquals(1.0, t.intercept, 1e-9)
        assertEquals(1.0, t.r2, 1e-9)
        assertNull(ReportStats.linearTrend(listOf(1.0, 2.0)))
        assertEquals(0.0, ReportStats.linearTrend(listOf(5.0, 5.0, 5.0))!!.r2, 1e-9)
    }

    @Test fun variationEnPourcentage() {
        assertEquals(50.0, ReportStats.percentChange(150.0, 100.0)!!, 1e-9)
        assertEquals(-50.0, ReportStats.percentChange(50.0, 100.0)!!, 1e-9)
        assertEquals(50.0, ReportStats.percentChange(-50.0, -100.0)!!, 1e-9)
        assertNull(ReportStats.percentChange(5.0, 0.0))
    }

    @Test fun confiance() {
        assertEquals(ReportConfidence.High, ReportStats.confidenceFromSample(30, 10, 20))
        assertEquals(ReportConfidence.Medium, ReportStats.confidenceFromSample(15, 10, 20))
        assertEquals(ReportConfidence.Low, ReportStats.confidenceFromSample(5, 10, 20))
        assertEquals("élevée", ReportConfidence.High.label)
    }

    @Test fun formatPourcentage() {
        assertEquals("+12,3 %", ReportStats.formatPercent(12.34))
        assertEquals("\u22125 %", ReportStats.formatPercent(-5.0))
        assertEquals("0 %", ReportStats.formatPercent(0.0))
        assertEquals("7 %", ReportStats.formatPercent(7.0, signed = false))
        assertEquals(14, ReportStats.MIN.trendDays)
    }
}
