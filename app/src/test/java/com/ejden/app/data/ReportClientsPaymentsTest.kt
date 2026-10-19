package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val RC_ZONE = ZoneOffset.UTC
private val RC_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RC_PREV = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
private val RC_TODAY = LocalDate.of(2026, 10, 19)
private val RC_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()
private val RC_LATER = Instant.parse("2026-12-01T12:00:00Z").toEpochMilli()

private val RC_CLIENTS = listOf(
    Client("c1", "Awa", "0101", "2026-10-02T08:00:00.000Z"),
    Client("c2", "Koffi", "0202", "2026-08-01T08:00:00.000Z"),
    Client("c3", "Mariam", "0303", "2026-07-01T08:00:00.000Z"),
    Client("c4", "Old", "0404", "2026-06-01T08:00:00.000Z")
)

private fun rcSale(
    id: String,
    createdAt: String,
    customer: String?,
    borrower: String?,
    method: PaymentMethod,
    remaining: Double,
    cancelled: String?,
    amount: Double
): Sale {
    val item = SaleItem("p1", "Produit", amount, null, 1.0, amount)
    return Sale(
        id, createdAt, listOf(item), amount, 0.0, amount, method,
        amount - remaining, 0.0, remaining, customer, borrower, cancelled
    )
}

private val RC_SALES = listOf(
    rcSale("a1", "2026-09-10T12:00:00.000Z", "c1", null, PaymentMethod.Cash, 0.0, null, 1000.0),
    rcSale("a2", "2026-09-25T12:00:00.000Z", "c1", null, PaymentMethod.Cash, 0.0, null, 1000.0),
    rcSale("a3", "2026-10-05T12:00:00.000Z", "c1", null, PaymentMethod.Cash, 0.0, null, 2000.0),
    rcSale("a4", "2026-10-06T12:00:00.000Z", "c2", null, PaymentMethod.Credit, 400.0, null, 1000.0),
    rcSale("a5", "2026-10-07T12:00:00.000Z", "c1", null, PaymentMethod.MobileMoney, 0.0, "2026-10-08T09:00:00.000Z", 1000.0),
    rcSale("a6", "2026-10-08T12:00:00.000Z", null, "Moussa", PaymentMethod.Credit, 500.0, null, 500.0),
    rcSale("a7", "2026-10-10T12:00:00.000Z", "c1", null, PaymentMethod.MobileMoney, 0.0, null, 500.0),
    rcSale("a8", "2026-08-01T12:00:00.000Z", "c4", null, PaymentMethod.Cash, 0.0, null, 700.0)
)

private val RC_PAYMENTS = listOf(
    CreditPayment("k1", "a4", 100.0, PaymentMethod.Cash, "2026-10-12T10:00:00.000Z"),
    CreditPayment("k2", "a1", 50.0, PaymentMethod.Cash, "2026-10-15T10:00:00.000Z"),
    CreditPayment("k3", "a4", 999.0, PaymentMethod.Cash, "2026-10-13T10:00:00.000Z", "2026-10-14T10:00:00.000Z"),
    CreditPayment("k4", "a1", 70.0, PaymentMethod.Cash, "2026-09-20T10:00:00.000Z")
)

private val RC_REMAINING = mapOf("a4" to 300.0, "a6" to 500.0)

private fun rcViews(filters: ReportFilters = ReportFilters(RC_RANGE)) =
    buildSaleViews(RC_SALES, filters, RC_RANGE, emptyMap(), RC_ZONE)

private fun rcClients(filters: ReportFilters = ReportFilters(RC_RANGE)): ReportClients =
    computeReportClients(rcViews(filters), RC_SALES, RC_CLIENTS, filters, RC_RANGE, RC_NOW, RC_ZONE)

private fun rcPayments(
    now: Long = RC_NOW,
    withCompare: Boolean = true
): ReportPayments {
    val filters = ReportFilters(RC_RANGE)
    val views = rcViews(filters)
    val first = computeReportHistory(RC_SALES, RC_TODAY, RC_ZONE).firstAt
    val cmp = if (withCompare) {
        computeReportComparison(RC_SALES, filters, RC_PREV, emptyMap(), first, RC_ZONE)
    } else {
        ReportComparison(null, null, emptyList())
    }
    return computeReportPayments(
        views, computeTotals(views), cmp, RC_CLIENTS, RC_PAYMENTS, RC_REMAINING,
        filters, RC_RANGE, now, RC_ZONE
    )
}

class ReportClientsPaymentsTest {
    @Test
    fun clientRowsAndSummary() {
        val r = rcClients()
        assertEquals(listOf("c1", "c2", "c3", "c4"), r.rows.map { it.id })
        val awa = r.rows[0]
        assertEquals(2, awa.count)
        assertEquals(2500.0, awa.amount, 1e-9)
        assertEquals(1250.0, awa.average, 1e-9)
        assertEquals(10.0, awa.intervalDays!!, 1e-9)
        assertEquals(ReportClientStatus.Active, awa.status)
        assertEquals(true, awa.isNew)
        val koffi = r.rows[1]
        assertEquals(1000.0, koffi.amount, 1e-9)
        assertNull(koffi.intervalDays)
        assertEquals(ReportClientStatus.Active, koffi.status)
        assertEquals(false, koffi.isNew)
        assertNull(r.rows[2].lastAt)
        assertEquals(ReportClientStatus.Inactive, r.rows[2].status)
        assertNotNull(r.rows[3].lastAt)
        assertEquals(ReportClientStatus.Inactive, r.rows[3].status)
        assertEquals(4, r.total)
        assertEquals(1, r.newCount)
        assertEquals(2, r.active)
        assertEquals(1, r.recurring)
        assertEquals(2, r.inactive)
        assertEquals(1750.0, r.avgAmount, 1e-9)
    }

    @Test
    fun clientFilterKeepsOnlyThatClient() {
        val r = rcClients(ReportFilters(RC_RANGE, clientId = "c2"))
        assertEquals(listOf("c2"), r.rows.map { it.id })
        assertEquals(1000.0, r.rows[0].amount, 1e-9)
    }

    @Test
    fun paymentMethodsAreRankedByAmount() {
        val p = rcPayments()
        assertEquals(listOf("Espèces", "Crédit", "Mobile Money"), p.methods.map { it.label })
        assertEquals(listOf(1, 2, 1), p.methods.map { it.count })
        assertEquals(listOf(50.0, 37.5, 12.5), p.methods.map { it.share })
    }

    @Test
    fun creditFiguresAndRepayments() {
        val p = rcPayments()
        assertEquals(800.0, p.toReceive, 1e-9)
        assertEquals(3200.0, p.collected, 1e-9)
        assertEquals(2, p.creditSales)
        assertEquals(1500.0, p.creditSalesAmount, 1e-9)
        assertEquals(150.0, p.repaid, 1e-9)
        assertEquals(37.5, p.creditShare, 1e-9)
        assertEquals(0.0, p.prevCreditShare!!, 1e-9)
        assertEquals(0.0, p.overdue, 1e-9)
        assertEquals(0, p.overdueCount)
        assertEquals(listOf("Moussa", "Koffi"), p.creditList.map { it.client })
        assertEquals(listOf(500.0, 300.0), p.creditList.map { it.remaining })
    }

    @Test
    fun overdueCreditsAfterThirtyDays() {
        val p = rcPayments(now = RC_LATER)
        assertEquals(800.0, p.overdue, 1e-9)
        assertEquals(2, p.overdueCount)
    }

    @Test
    fun noPreviousCreditShareWithoutComparison() {
        assertNull(rcPayments(withCompare = false).prevCreditShare)
    }
}
