package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY: LocalDate = LocalDate.of(2026, 10, 9)

private fun utc(text: String): Long = Instant.parse(text).toEpochMilli()

private class DashEnv {
    val store = InMemoryStore()
    val products = ProductRepository(store)
    val sales = SaleRepository(store, products)
    val credits = CreditPaymentRepository(store, sales)
    val clients = ClientRepository(store, sales, credits)
    val expenses = ExpenseRepository(store)
    val cash = CashRepository(store, sales, credits, expenses)
    val dashboard = DashboardRepository(sales, cash, clients, credits, products)
}

private fun dashSale(
    id: String,
    total: Double,
    createdAt: String,
    cost: Double? = null,
    remaining: Double = 0.0,
    borrower: String? = null
) = Sale(
    id, createdAt, listOf(SaleItem("p", "P", total, cost, 1.0, total)),
    total, 0.0, total,
    if (remaining > 0.0) PaymentMethod.Credit else PaymentMethod.Cash,
    total - remaining, 0.0, remaining, null, borrower
)

class PeriodDashboardTest {

    @Test
    fun nombresFormatesCommeEnFrancais() {
        assertEquals("1\u202F234\u202F567", formatNumber(1234567.0))
        assertEquals("999", formatNumber(999.0))
        assertEquals("1\u202F000", formatNumber(1000.0))
        assertEquals("-1\u202F500", formatNumber(-1500.4))
        assertEquals("1", formatNumber(0.5))
        assertEquals("0", formatNumber(0.0))
    }

    @Test
    fun joursValides() {
        assertTrue(isValidDay("2026-10-04"))
        assertFalse(isValidDay("2026-02-30"))
        assertFalse(isValidDay("1999-12-31"))
        assertFalse(isValidDay("2026-1-4"))
        assertFalse(isValidDay(null))
    }

    @Test
    fun periodeMemoriseeEtRepliSurAujourdhui() {
        val store = InMemoryStore()
        val repo = PeriodRepository(store)
        assertEquals(PeriodChoice(PeriodPreset.Today), repo.get())

        assertTrue(repo.save(PeriodChoice(PeriodPreset.Days7)))
        assertEquals(PeriodChoice(PeriodPreset.Days7), repo.get())

        val custom = PeriodChoice(PeriodPreset.Custom, "2026-10-01", "2026-10-05")
        repo.save(custom)
        assertEquals(custom, repo.get())

        repo.save(PeriodChoice(PeriodPreset.Custom, "2026-10-05", "2026-10-01"))
        assertEquals(PeriodChoice(PeriodPreset.Today), repo.get())

        store.set(PeriodRepository.KEY, "n'importe quoi")
        assertEquals(PeriodChoice(PeriodPreset.Today), repo.get())
    }

    @Test
    fun periodesCalculeesEtPeriodePrecedente() {
        val today = resolvePeriod(PeriodChoice(PeriodPreset.Today), TODAY, ZoneOffset.UTC)
        assertEquals(utc("2026-10-09T00:00:00Z"), today.startMillis)
        assertEquals(utc("2026-10-09T23:59:59.999Z"), today.endMillis)
        assertEquals(utc("2026-10-08T00:00:00Z"), today.previousStartMillis)
        assertEquals(utc("2026-10-08T23:59:59.999Z"), today.previousEndMillis)
        assertEquals("Aujourd'hui", today.label)
        assertEquals("hier", today.comparedTo)

        val week = resolvePeriod(PeriodChoice(PeriodPreset.Days7), TODAY, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 10, 3), week.startDay)
        assertEquals(utc("2026-09-26T00:00:00Z"), week.previousStartMillis)
        assertEquals(utc("2026-10-02T23:59:59.999Z"), week.previousEndMillis)
        assertEquals("7 jours", week.label)
        assertEquals("période précédente", week.comparedTo)

        val month = resolvePeriod(PeriodChoice(PeriodPreset.Month1), TODAY, ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 9, 10), month.startDay)

        // 31 mars - 1 mois = 28 février, puis +1 jour : 1er mars.
        val march = resolvePeriod(PeriodChoice(PeriodPreset.Month1), LocalDate.of(2026, 3, 31), ZoneOffset.UTC)
        assertEquals(LocalDate.of(2026, 3, 1), march.startDay)
    }

    @Test
    fun libellesDePeriodePersonnalisee() {
        val range = resolvePeriod(
            PeriodChoice(PeriodPreset.Custom, "2026-10-01", "2026-10-05"), TODAY, ZoneOffset.UTC
        )
        assertTrue(range.label.contains("oct"))
        assertTrue(range.label.contains("\u2013"))

        val oneDay = resolvePeriod(
            PeriodChoice(PeriodPreset.Custom, "2026-10-04", "2026-10-04"), TODAY, ZoneOffset.UTC
        )
        assertFalse(oneDay.label.contains("\u2013"))

        val oldYear = resolvePeriod(
            PeriodChoice(PeriodPreset.Custom, "2025-10-04", "2025-10-04"), TODAY, ZoneOffset.UTC
        )
        assertTrue(oldYear.label.contains("2025"))
    }

    @Test
    fun variations() {
        assertEquals(Change("\u25B2 +50 %", ChangeTone.Up), describeChange(150.0, 100.0))
        assertEquals(Change("\u25BC \u221250 %", ChangeTone.Down), describeChange(50.0, 100.0))
        assertEquals(Change("= stable", ChangeTone.Neutral), describeChange(100.0, 100.0))
        assertEquals(Change("Nouveau", ChangeTone.Up), describeChange(10.0, 0.0))
        assertEquals(Change("\u2014", ChangeTone.Neutral), describeChange(0.0, 0.0))
        assertNull(percentChange(10.0, 0.0))
    }

    @Test
    fun tableauDeBordCalculeSurLaPeriode() {
        val env = DashEnv()
        env.sales.saveSales(
            listOf(
                dashSale("s1", 1000.0, "2026-10-09T09:00:00.000Z", cost = 600.0),
                dashSale("s2", 500.0, "2026-10-08T15:00:00.000Z"),
                dashSale("s3", 2000.0, "2026-10-09T10:00:00.000Z", remaining = 2000.0, borrower = "Awa")
            )
        )
        env.clients.addClient(Client("c1", "Awa", "", "2026-10-01T10:00:00.000Z"))
        env.products.saveProducts(
            listOf(Product("a", "Riz", 1.0, 2.0, 0, 5, null, null, null))
        )

        val period = resolvePeriod(PeriodChoice(PeriodPreset.Today), TODAY, ZoneOffset.UTC)
        val data = env.dashboard.load(period)

        assertEquals(2, data.summary.salesCount)
        assertEquals(3000.0, data.summary.revenue, 0.0)
        assertEquals(400.0, data.summary.margin!!, 0.0)
        assertEquals(1, data.previous.salesCount)
        assertEquals(500.0, data.previous.revenue, 0.0)

        assertEquals(1500.0, data.cashBalance, 0.0)
        assertEquals(1000.0, data.cashFlow.cashIn, 0.0)
        assertEquals(0.0, data.cashFlow.cashOut, 0.0)

        assertEquals(1, data.clientsCount)
        assertEquals(2000.0, data.receivables.amount, 0.0)
        assertEquals(1, data.receivables.debtorsCount)
        assertEquals(1, data.stock.outCount)
    }
}
