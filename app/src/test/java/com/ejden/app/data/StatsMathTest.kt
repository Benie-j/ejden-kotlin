package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun statSale(id: String, createdAt: String, cancelled: String? = null) = Sale(
    id, createdAt, emptyList(), 0.0, 0.0, 0.0, PaymentMethod.Cash,
    0.0, 0.0, 0.0, null, null, cancelled
)

class StatsMathTest {

    @Test fun nombresFrancais() {
        assertEquals("1\u202F235", statNumber(1234.56))
        assertEquals("1\u202F234,6", statNumber(1234.56, 1))
        assertEquals("12,3 %", statPct(12.34, 1))
        assertEquals("+5 %", statSignedPct(5.0))
        assertEquals("-5 %", statSignedPct(-5.0))
        assertEquals("1,5", statQty(1.5))
    }

    @Test fun formatCompact() {
        assertEquals("1,5 M", statCompact(1_500_000.0))
        assertEquals("1 M", statCompact(1_000_000.0))
        assertEquals("3 k", statCompact(2_500.0))
        assertEquals("\u22121 k", statCompact(-1_200.0))
        assertEquals("950", statCompact(950.0))
    }

    @Test fun regressionLineaire() {
        val fit = linearFit(listOf(1.0, 2.0, 3.0, 4.0))
        assertEquals(1.0, fit.slope, 1e-9)
        assertEquals(1.0, fit.intercept, 1e-9)
        assertEquals(0.0, fit.sd, 1e-9)
    }

    @Test fun projection() {
        val p = project(listOf(10.0, 20.0, 30.0), 2)
        assertEquals(listOf(40.0, 50.0), p.values)
        assertEquals(10.0, p.slope, 1e-9)
        assertEquals(20.0, p.mean, 1e-9)
    }

    @Test fun projectionPlafonneeEtJamaisNegative() {
        assertEquals(60.0, project(listOf(10.0, 20.0, 30.0), 10).values.last(), 1e-9)
        assertEquals(0.0, project(listOf(30.0, 20.0, 10.0), 5).values.last(), 1e-9)
    }

    @Test fun semaines() {
        val lundi = LocalDate.of(2026, 10, 19)
        assertEquals(0, statWeekday(lundi))
        assertEquals(6, statWeekday(LocalDate.of(2026, 10, 25)))
        assertEquals(lundi, statWeekStart(LocalDate.of(2026, 10, 21)))
        val millis = Instant.parse("2026-10-19T23:30:00Z").toEpochMilli()
        assertEquals(lundi, statDay(millis, ZoneOffset.UTC))
    }

    @Test fun ventesActivesSeulement() {
        val rows = activeStatRows(
            listOf(
                statSale("a", "2026-10-19T10:00:00.000Z"),
                statSale("b", "2026-10-19T11:00:00.000Z", cancelled = "2026-10-19T12:00:00.000Z"),
                statSale("c", "pas une date")
            )
        )
        assertEquals(1, rows.size)
        assertEquals("a", rows[0].sale.id)
    }
}
