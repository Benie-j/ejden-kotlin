package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val FC_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
private val FC_ZONE = ZoneOffset.UTC

private fun fcSale(id: String, createdAt: String, price: Double = 1000.0, cost: Double? = 600.0): Sale {
    val item = SaleItem("p", "Produit", price, cost, 1.0, price)
    return Sale(id, createdAt, listOf(item), price, 0.0, price, PaymentMethod.Cash, price, 0.0, 0.0, null, null, null)
}

/** Une vente de 1000 par jour, du 19/09 au 18/10/2026 (30 jours). */
private fun fcDaily(cost: Double? = 600.0): List<StatRow> {
    val sales = ArrayList<Sale>()
    var d = LocalDate.of(2026, 9, 19)
    while (d.isBefore(LocalDate.of(2026, 10, 19))) {
        sales.add(fcSale("s$d", "${d}T12:00:00.000Z", cost = cost))
        d = d.plusDays(1)
    }
    return activeStatRows(sales)
}

class StatsForecastTest {

    @Test fun aucuneVenteDonneNull() {
        assertNull(computeForecast(emptyList(), emptyList(), FC_NOW, FC_ZONE))
    }

    @Test fun courtTerme() {
        val s = computeForecast(fcDaily(), emptyList(), FC_NOW, FC_ZONE)!!.shortTerm
        assertEquals(30, s.daysCovered)
        assertEquals(7000.0, s.next7, 1e-6)
        assertEquals(30000.0, s.next30, 1e-6)
        assertEquals(18000.0, s.realized, 1e-6)
        assertEquals(12000.0, s.remaining, 1e-6)
        assertEquals(30000.0, s.projectedMonth, 1e-6)
        assertEquals(9, s.monthIndex)
    }

    @Test fun semaines() {
        val w = computeForecast(fcDaily(), emptyList(), FC_NOW, FC_ZONE)!!.weekly!!
        assertEquals(listOf(2000.0, 7000.0, 7000.0, 7000.0, 7000.0), w.chart.values)
        assertEquals("14/9", w.chart.labels[0])
        assertEquals(listOf("19/10", "26/10", "2/11", "9/11"), w.chart.forecastLabels)
        assertEquals(4, w.chart.forecast.size)
        assertEquals(Trend.Up, w.trend)
    }

    @Test fun moisInsuffisantsDonnentNull() {
        assertNull(computeForecast(fcDaily(), emptyList(), FC_NOW, FC_ZONE)!!.monthly)
    }

    @Test fun moisEtCroissance() {
        val sales = listOf(
            fcSale("a", "2026-06-10T10:00:00.000Z", 1000.0),
            fcSale("b", "2026-07-10T10:00:00.000Z", 2000.0),
            fcSale("c", "2026-08-10T10:00:00.000Z", 3000.0),
            fcSale("d", "2026-09-10T10:00:00.000Z", 4000.0)
        )
        val m = computeForecast(activeStatRows(sales), emptyList(), FC_NOW, FC_ZONE)!!.monthly!!
        assertEquals(listOf(1000.0, 2000.0, 3000.0, 4000.0), m.chart.values)
        assertEquals(listOf(5000.0, 6000.0, 7000.0), m.chart.forecast)
        assertEquals(listOf("oct.", "nov.", "déc."), m.chart.forecastLabels)
        assertEquals(18000.0, m.forecastTotal, 1e-6)
        assertEquals("sept.", m.bestLabel)
        assertEquals("juin", m.worstLabel)
        assertEquals(33.3333333, m.lastVsPrevPct!!, 1e-4)
        assertNull(m.recent3VsBefore3Pct)
        assertNull(m.yearOnYearPct)
    }

    @Test fun seuilDeRentabilite() {
        val expenses = listOf(
            Expense("e1", "Loyer", 30000.0, ExpenseCategory.Rent, "2026-08-05T10:00:00.000Z"),
            Expense("e2", "Stock", 5000.0, ExpenseCategory.Merchandise, "2026-09-05T10:00:00.000Z"),
            Expense("e3", "Loyer", 99999.0, ExpenseCategory.Rent, "2026-10-02T10:00:00.000Z")
        )
        val be = computeForecast(fcDaily(), expenses, FC_NOW, FC_ZONE)!!.breakEven as BreakEven.Ready
        assertEquals(10000.0, be.monthlyCharges, 1e-6)
        assertEquals(0.4, be.marginRate, 1e-9)
        assertEquals(25000.0, be.threshold, 1e-6)
        assertEquals(1.2, be.ratio!!, 1e-9)
    }

    @Test fun seuilSansChargesOuSansPrixAchat() {
        assertEquals(BreakEven.NoCharges, computeForecast(fcDaily(), emptyList(), FC_NOW, FC_ZONE)!!.breakEven)
        val expenses = listOf(Expense("e1", "Loyer", 30000.0, ExpenseCategory.Rent, "2026-08-05T10:00:00.000Z"))
        assertEquals(BreakEven.NoData, computeForecast(fcDaily(cost = null), expenses, FC_NOW, FC_ZONE)!!.breakEven)
    }

    @Test fun joursEtHeuresDePointe() {
        val r = computeForecast(fcDaily(), emptyList(), FC_NOW, FC_ZONE)!!.rhythm
        assertNotNull(r)
        assertEquals(12, r!!.bestHour)
        assertEquals(30, r.grid.sumOf { it.sum() })
        assertTrue(r.grid.all { it[6] > 0 })
    }

    @Test fun pointeAbsenteSousDixVentes() {
        val sales = (1..5).map { fcSale("x$it", "2026-10-1${it}T10:00:00.000Z") }
        assertNull(computeForecast(activeStatRows(sales), emptyList(), FC_NOW, FC_ZONE)!!.rhythm)
    }
}
