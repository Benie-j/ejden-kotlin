package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val DUP_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")

class InvoiceDuplicateTest {

    @Test
    fun copieEnBrouillonSansNumeroNiPaiements() {
        val store = InMemoryStore()
        val numbering = DocumentNumbering(store, now = { DUP_NOW }, zone = ZoneOffset.UTC)
        val quotes = QuoteRepository(store, numbering, now = { DUP_NOW }, zone = ZoneOffset.UTC)
        val documents = DocumentRepository(store)
        val invoices = InvoiceRepository(store, numbering, quotes, documents, now = { DUP_NOW }, zone = ZoneOffset.UTC)

        val source = Invoice(
            id = "f1",
            number = "FAC-2026-0001",
            status = InvoiceStatus.Issued,
            createdAt = "2026-09-01T09:00:00.000Z",
            updatedAt = "2026-09-01T09:00:00.000Z",
            issueDate = "2026-09-01",
            dueDate = "2026-09-30",
            quoteId = "q1",
            quoteNumber = "DEV-2026-0001",
            clientName = "Awa Koné",
            clientPhone = "07 11",
            subject = "Matériel",
            lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
            taxRate = 18.0,
            payments = listOf(
                InvoicePayment("p1", "2026-09-05", 1000.0, PaymentMethod.Cash, "", "REC-2026-0001", "2026-09-05T09:00:00.000Z")
            )
        )
        assertTrue(invoices.saveInvoice(source))

        val copy = invoices.duplicateInvoice("f1")!!
        assertNotEquals("f1", copy.id)
        assertEquals(InvoiceStatus.Draft, copy.status)
        assertEquals("", copy.number)
        assertEquals("2026-10-15", copy.issueDate)
        assertEquals(addDaysToDay("2026-10-15", documents.getSettings().defaultDueDays), copy.dueDate)
        assertNull(copy.quoteId)
        assertEquals("", copy.quoteNumber)
        assertTrue(copy.payments.isEmpty())
        assertEquals("Matériel", copy.subject)
        assertEquals(2360.0, copy.totals().total, 0.0)

        // La facture d'origine reste intacte.
        assertEquals(2, invoices.getInvoices().size)
        assertEquals(1, invoices.getInvoiceById("f1")!!.payments.size)
        assertNull(invoices.duplicateInvoice("zz"))
    }
}
