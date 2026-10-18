package com.ejden.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val INV_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")

private class InvoiceEnv {
    val store = InMemoryStore()
    val numbering = DocumentNumbering(store, now = { INV_NOW }, zone = ZoneOffset.UTC)
    val quotes = QuoteRepository(store, numbering, now = { INV_NOW }, zone = ZoneOffset.UTC)
    val documents = DocumentRepository(store)
    val invoices = InvoiceRepository(store, numbering, quotes, documents, now = { INV_NOW }, zone = ZoneOffset.UTC)
}

private fun sampleInvoice(
    id: String = "f1",
    status: InvoiceStatus = InvoiceStatus.Draft,
    number: String = "",
    createdAt: String = "2026-10-10T09:00:00.000Z",
    dueDate: String = "2026-11-09",
    payments: List<InvoicePayment> = emptyList()
) = Invoice(
    id = id,
    number = number,
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
    issueDate = "2026-10-10",
    dueDate = dueDate,
    clientName = "Awa Koné",
    clientPhone = "07 11",
    lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
    taxRate = 18.0,
    payments = payments
)

private fun samplePayment(
    id: String,
    date: String,
    amount: Double,
    createdAt: String,
    receiptNumber: String = ""
) = InvoicePayment(id, date, amount, PaymentMethod.Cash, "", receiptNumber, createdAt)

private fun quoteForInvoice(id: String, status: QuoteStatus) = Quote(
    id = id,
    number = "DEV-2026-0001",
    status = status,
    createdAt = "2026-10-10T09:00:00.000Z",
    updatedAt = "2026-10-10T09:00:00.000Z",
    issueDate = "2026-10-10",
    validUntil = "2026-11-09",
    clientName = "Awa Koné",
    clientPhone = "07 11",
    lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
    taxRate = 18.0
)

class InvoiceTest {

    @Test
    fun enregistrerRemplacerEtTrier() {
        val e = InvoiceEnv()
        val older = sampleInvoice("f1", createdAt = "2026-10-10T09:00:00.000Z")
        val newer = sampleInvoice("f2", createdAt = "2026-10-12T09:00:00.000Z")

        assertTrue(e.invoices.saveInvoice(older))
        assertTrue(e.invoices.saveInvoice(newer))
        assertEquals(listOf("f2", "f1"), e.invoices.getInvoices().map { it.id })

        assertTrue(e.invoices.saveInvoice(older.copy(subject = "Mise à jour")))
        assertEquals(2, e.invoices.getInvoices().size)
        assertEquals("Mise à jour", e.invoices.getInvoiceById("f1")!!.subject)

        assertFalse(e.invoices.saveInvoice(older.copy(clientName = "")))
        assertEquals(2360.0, e.invoices.getInvoiceById("f1")!!.totals().total, 0.0)
    }

    @Test
    fun lectureRefuseLesFacturesIncoherentes() {
        val draft = sampleInvoice()
        assertEquals(draft, normalizeInvoice(draft.toJson()))

        // Un brouillon n'a pas de numéro ; une facture émise en a besoin.
        assertNotNull(normalizeInvoice(draft.toJson()))
        assertNull(normalizeInvoice(draft.copy(status = InvoiceStatus.Issued).toJson()))
        assertNotNull(normalizeInvoice(draft.copy(status = InvoiceStatus.Issued, number = "FAC-2026-0001").toJson()))

        assertNull(normalizeInvoice(draft.toJson().put("lines", JSONArray())))
        assertNull(normalizeInvoice(draft.toJson().put("dueDate", "pas une date")))
        assertNull(normalizeInvoice("texte"))
    }

    @Test
    fun cycleEmissionPaiementsEtStatuts() {
        val e = InvoiceEnv()
        assertTrue(e.invoices.saveInvoice(sampleInvoice()))

        // Un brouillon n'accepte pas de paiement.
        assertEquals(
            InvoicePaymentResult.NotIssued,
            e.invoices.addPayment("f1", 100.0, PaymentMethod.Cash, "2026-10-15", "")
        )

        val issued = e.invoices.issueInvoice("f1")!!
        assertEquals("FAC-2026-0001", issued.number)
        assertEquals(InvoiceStatus.Issued, issued.status)
        assertEquals(InvoiceDisplayStatus.Unpaid, e.invoices.displayStatus(issued))
        assertNull(e.invoices.issueInvoice("f1"))

        assertEquals(
            InvoicePaymentResult.Ok,
            e.invoices.addPayment("f1", 1000.0, PaymentMethod.Cash, "2026-10-15", "acompte")
        )
        val partial = e.invoices.getInvoiceById("f1")!!
        assertEquals(1000.0, partial.paid(), 0.0)
        assertEquals(1360.0, partial.remaining(), 0.0)
        assertEquals("REC-2026-0001", partial.payments[0].receiptNumber)
        assertEquals(InvoiceDisplayStatus.Partial, e.invoices.displayStatus(partial))
        assertEquals(InvoiceDisplayStatus.Overdue, e.invoices.displayStatus(partial, "2026-11-10"))

        assertEquals(
            InvoicePaymentResult.TooMuch,
            e.invoices.addPayment("f1", 1361.0, PaymentMethod.Cash, "2026-10-15", "")
        )
        assertEquals(
            InvoicePaymentResult.Invalid,
            e.invoices.addPayment("f1", 0.0, PaymentMethod.Cash, "2026-10-15", "")
        )
        assertEquals(
            InvoicePaymentResult.Invalid,
            e.invoices.addPayment("f1", 100.0, PaymentMethod.Credit, "2026-10-15", "")
        )
        assertEquals(
            InvoicePaymentResult.NotFound,
            e.invoices.addPayment("zz", 100.0, PaymentMethod.Cash, "2026-10-15", "")
        )

        assertEquals(
            InvoicePaymentResult.Ok,
            e.invoices.addPayment("f1", 1360.0, PaymentMethod.MobileMoney, "2026-10-15", "")
        )
        val paid = e.invoices.getInvoiceById("f1")!!
        assertEquals(InvoiceDisplayStatus.Paid, e.invoices.displayStatus(paid))
        assertEquals("REC-2026-0002", paid.payments[1].receiptNumber)

        // Une facture avec des paiements ne s'annule pas ; sans paiement, oui.
        assertFalse(e.invoices.cancelInvoice("f1"))
        for (payment in paid.payments) assertTrue(e.invoices.removePayment("f1", payment.id))
        assertEquals(0, e.invoices.getInvoiceById("f1")!!.payments.size)
        assertTrue(e.invoices.cancelInvoice("f1"))
        assertEquals(
            InvoiceDisplayStatus.Cancelled,
            e.invoices.displayStatus(e.invoices.getInvoiceById("f1")!!)
        )
    }

    @Test
    fun seulsLesBrouillonsSeSupprimentEtLesAutresSeFiltrent() {
        val e = InvoiceEnv()
        assertTrue(e.invoices.saveInvoice(sampleInvoice("f1")))
        assertTrue(
            e.invoices.saveInvoice(
                sampleInvoice("f2", status = InvoiceStatus.Issued, number = "FAC-2026-0001")
            )
        )

        assertFalse(e.invoices.deleteInvoice("f2"))
        assertFalse(e.invoices.deleteInvoice("zz"))
        assertTrue(e.invoices.deleteInvoice("f1"))
        assertEquals(listOf("f2"), e.invoices.getInvoices().map { it.id })
    }

    @Test
    fun factureDepuisUnDevisAccepteUneSeuleFois() {
        val e = InvoiceEnv()
        assertTrue(e.quotes.saveQuote(quoteForInvoice("q1", QuoteStatus.Accepted)))
        assertTrue(e.quotes.saveQuote(quoteForInvoice("q2", QuoteStatus.Draft)))

        val invoice = e.invoices.createInvoiceFromQuote("q1")!!
        val settings = e.documents.getSettings()
        assertEquals(InvoiceStatus.Draft, invoice.status)
        assertEquals("", invoice.number)
        assertEquals("q1", invoice.quoteId)
        assertEquals("DEV-2026-0001", invoice.quoteNumber)
        assertEquals("2026-10-15", invoice.issueDate)
        assertEquals(addDaysToDay("2026-10-15", settings.defaultDueDays), invoice.dueDate)
        assertEquals(settings.invoiceTerms, invoice.terms)
        assertEquals(2360.0, invoice.totals().total, 0.0)
        assertEquals(invoice.id, e.quotes.getQuoteById("q1")!!.invoiceId)

        assertNull(e.invoices.createInvoiceFromQuote("q1"))
        assertNull(e.invoices.createInvoiceFromQuote("q2"))
        assertNull(e.invoices.createInvoiceFromQuote("zz"))
    }

    @Test
    fun recusNumerosEtResteApresChaquePaiement() {
        val e = InvoiceEnv()
        val invoice = sampleInvoice(
            "f1",
            status = InvoiceStatus.Issued,
            number = "FAC-2026-0001",
            payments = listOf(
                samplePayment("p1", "2026-10-12", 1000.0, "2026-10-12T09:00:00.000Z", "REC-2026-0001"),
                samplePayment("p2", "2026-10-14", 500.0, "2026-10-14T09:00:00.000Z")
            )
        )
        assertTrue(e.invoices.saveInvoice(invoice))
        assertTrue(e.invoices.saveInvoice(sampleInvoice("f3")))

        val receipts = e.invoices.getReceipts()
        assertEquals(listOf("f1:p2", "f1:p1"), receipts.map { it.key })
        assertEquals("REC-FAC-2026-0001-2", receipts[0].number)
        assertEquals(860.0, receipts[0].remainingAfter, 0.0)
        assertEquals("REC-2026-0001", receipts[1].number)
        assertEquals(1360.0, receipts[1].remainingAfter, 0.0)
        assertNotNull(e.invoices.getReceiptByKey("f1:p1"))
        assertNull(e.invoices.getReceiptByKey("f1:zz"))
    }

    @Test
    fun encaisseSurUnePeriodeEtResteAEncaisser() {
        val e = InvoiceEnv()
        assertTrue(
            e.invoices.saveInvoice(
                sampleInvoice(
                    "f1",
                    status = InvoiceStatus.Issued,
                    number = "FAC-2026-0001",
                    payments = listOf(
                        samplePayment("p1", "2026-10-12", 1000.0, "2026-10-12T09:00:00.000Z"),
                        samplePayment("p2", "2026-10-14", 500.0, "2026-10-14T09:00:00.000Z")
                    )
                )
            )
        )
        assertTrue(
            e.invoices.saveInvoice(
                sampleInvoice(
                    "f2",
                    status = InvoiceStatus.Issued,
                    number = "FAC-2026-0002",
                    dueDate = "2026-10-01"
                )
            )
        )
        assertTrue(e.invoices.saveInvoice(sampleInvoice("f3")))

        val oneDay = e.invoices.collectedBetween(
            Instant.parse("2026-10-14T00:00:00Z"),
            Instant.parse("2026-10-14T23:59:59Z")
        )
        assertEquals(500.0, oneDay.amount, 0.0)
        assertEquals(1, oneDay.count)

        val all = e.invoices.collectedBetween(
            Instant.parse("2026-10-01T00:00:00Z"),
            Instant.parse("2026-10-31T23:59:59Z")
        )
        assertEquals(1500.0, all.amount, 0.0)
        assertEquals(2, all.count)

        val due = e.invoices.receivable()
        assertEquals(3220.0, due.total, 0.0)
        assertEquals(2, due.count)
        assertEquals(2360.0, due.overdueTotal, 0.0)
        assertEquals(1, due.overdueCount)
    }
}
