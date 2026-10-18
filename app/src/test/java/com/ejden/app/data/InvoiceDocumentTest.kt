package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private val DOC_SETTINGS = normalizeDocumentSettings(JSONObject())

private fun docInvoice(
    status: InvoiceStatus = InvoiceStatus.Issued,
    number: String = "FAC-2026-0001",
    quoteNumber: String = "",
    payments: List<InvoicePayment> = emptyList()
) = Invoice(
    id = "f1",
    number = number,
    status = status,
    createdAt = "2026-10-10T09:00:00.000Z",
    updatedAt = "2026-10-10T09:00:00.000Z",
    issueDate = "2026-10-10",
    dueDate = "2026-11-09",
    quoteNumber = quoteNumber,
    clientName = "Awa Koné",
    clientPhone = "07 11",
    lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
    taxRate = 18.0,
    payments = payments
)

class InvoiceDocumentTest {

    @Test
    fun brouillonSansNumeroNiSolde() {
        val doc = invoiceToDocument(
            docInvoice(InvoiceStatus.Draft, number = ""),
            DOC_SETTINGS,
            InvoiceDisplayStatus.Draft
        )
        assertEquals("FACTURE", doc.title)
        assertEquals("(brouillon)", doc.number)
        assertEquals("BROUILLON", doc.stamp)
        assertEquals("Échéance", doc.dueLabel)
        assertEquals("2026-11-09", doc.dueDate)
        assertEquals("Facturé à", doc.party.label)
        assertNull(doc.balanceDue)
        assertNull(doc.signatures)
        assertEquals(2360.0, doc.totals.total, 0.0)
    }

    @Test
    fun factureEmiseAvecPaiementAfficheLeSolde() {
        val payment = InvoicePayment(
            "p1", "2026-10-12", 1000.0, PaymentMethod.Cash, "", "REC-2026-0001", "2026-10-12T09:00:00.000Z"
        )
        val doc = invoiceToDocument(
            docInvoice(payments = listOf(payment)),
            DOC_SETTINGS,
            InvoiceDisplayStatus.Partial
        )
        assertNull(doc.stamp)
        assertEquals(1360.0, doc.balanceDue!!, 0.0)
        assertEquals(1, doc.payments.size)
        assertEquals("Espèces", doc.payments[0].label)
        assertEquals(1000.0, doc.payments[0].amount, 0.0)
        assertEquals("2026-10-12", doc.payments[0].date)
    }

    @Test
    fun tamponsSelonLeStatutAffiche() {
        val invoice = docInvoice()
        fun stampOf(status: InvoiceDisplayStatus) = invoiceToDocument(invoice, DOC_SETTINGS, status).stamp

        assertEquals("PAYÉE", stampOf(InvoiceDisplayStatus.Paid))
        assertEquals("ANNULÉE", stampOf(InvoiceDisplayStatus.Cancelled))
        assertEquals("EN RETARD", stampOf(InvoiceDisplayStatus.Overdue))
        assertNull(stampOf(InvoiceDisplayStatus.Unpaid))
        assertNull(stampOf(InvoiceDisplayStatus.Partial))
    }

    @Test
    fun referenceDuDevisEtFactureAnnuleeSansSolde() {
        val withQuote = invoiceToDocument(
            docInvoice(quoteNumber = "DEV-2026-0001"),
            DOC_SETTINGS,
            InvoiceDisplayStatus.Unpaid
        )
        assertEquals("Référence", withQuote.blocks[0].title)
        assertEquals("Devis n° DEV-2026-0001", withQuote.blocks[0].text)

        val without = invoiceToDocument(docInvoice(), DOC_SETTINGS, InvoiceDisplayStatus.Unpaid)
        assertEquals("Modalités de paiement", without.blocks[0].title)

        val cancelled = invoiceToDocument(
            docInvoice(InvoiceStatus.Cancelled),
            DOC_SETTINGS,
            InvoiceDisplayStatus.Cancelled
        )
        assertNull(cancelled.balanceDue)
    }
}
