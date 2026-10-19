package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val CASH_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")

private class InvoiceCashEnv {
    val store = InMemoryStore()
    val numbering = DocumentNumbering(store, now = { CASH_NOW }, zone = ZoneOffset.UTC)
    val quotes = QuoteRepository(store, numbering, now = { CASH_NOW }, zone = ZoneOffset.UTC)
    val documents = DocumentRepository(store)
    val invoices = InvoiceRepository(store, numbering, quotes, documents, now = { CASH_NOW }, zone = ZoneOffset.UTC)

    fun save(id: String, status: InvoiceStatus, payments: List<InvoicePayment>) {
        invoices.saveInvoice(
            Invoice(
                id = id,
                number = "FAC-2026-0001",
                status = status,
                createdAt = "2026-10-10T09:00:00.000Z",
                updatedAt = "2026-10-10T09:00:00.000Z",
                issueDate = "2026-10-10",
                dueDate = "2026-11-09",
                clientName = "Awa Koné",
                clientPhone = "07 11",
                lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
                taxRate = 18.0,
                payments = payments
            )
        )
    }
}

private fun cashPayment(id: String, date: String, amount: Double, method: PaymentMethod, createdAt: String) =
    InvoicePayment(id, date, amount, method, "", "REC-2026-0001", createdAt)

class InvoiceCashTest {

    @Test
    fun seulesLesFacturesEmisesAlimententLaCaisse() {
        val e = InvoiceCashEnv()
        e.save(
            "f1", InvoiceStatus.Issued,
            listOf(
                cashPayment("p1", "2026-10-12", 1000.0, PaymentMethod.Cash, "2026-10-12T09:00:00.000Z"),
                // Saisi le 15 pour le 14 : le jour saisi fait foi (midi, heure locale).
                cashPayment("p2", "2026-10-14", 500.0, PaymentMethod.MobileMoney, "2026-10-15T08:00:00.000Z")
            )
        )
        e.save(
            "f2", InvoiceStatus.Cancelled,
            listOf(cashPayment("p3", "2026-10-12", 300.0, PaymentMethod.Cash, "2026-10-12T09:00:00.000Z"))
        )

        val movements = e.invoices.cashMovements()
        assertEquals(
            setOf("invoice-payment-f1-p1", "invoice-payment-f1-p2"),
            movements.map { it.id }.toSet()
        )

        val first = movements.first { it.id == "invoice-payment-f1-p1" }
        assertEquals(CashSource.InvoicePayment, first.source)
        assertEquals(1000.0, first.amount, 0.0)
        assertEquals("Facture FAC-2026-0001 · Awa Koné", first.label)
        assertEquals(PaymentMethod.Cash, first.method)
        assertEquals(Instant.parse("2026-10-12T09:00:00Z").toEpochMilli(), parseDateMillis(first.createdAt))

        val second = movements.first { it.id == "invoice-payment-f1-p2" }
        assertEquals(Instant.parse("2026-10-14T12:00:00Z").toEpochMilli(), parseDateMillis(second.createdAt))
    }

    @Test
    fun laCaisseCompteLesPaiementsParMode() {
        val e = InvoiceCashEnv()
        e.save(
            "f1", InvoiceStatus.Issued,
            listOf(
                cashPayment("p1", "2026-10-12", 1000.0, PaymentMethod.Cash, "2026-10-12T09:00:00.000Z"),
                cashPayment("p2", "2026-10-14", 500.0, PaymentMethod.MobileMoney, "2026-10-14T09:00:00.000Z")
            )
        )

        val products = ProductRepository(e.store)
        val sales = SaleRepository(e.store, products)
        val credits = CreditPaymentRepository(e.store, sales)
        val expenses = ExpenseRepository(e.store)
        val cash = CashRepository(
            e.store, sales, credits, expenses,
            extraMovements = { e.invoices.cashMovements() }
        )

        val summary = cash.getCashSummary(CASH_NOW, ZoneOffset.UTC)
        assertEquals(1500.0, summary.balance, 0.0)
        assertEquals(1000.0, summary.salesByMethod[PaymentMethod.Cash]!!, 0.0)
        assertEquals(500.0, summary.salesByMethod[PaymentMethod.MobileMoney]!!, 0.0)
        assertEquals(2, summary.movements.size)
    }
}
