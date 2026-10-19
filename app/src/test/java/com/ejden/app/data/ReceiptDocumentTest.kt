package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun receiptFor(subject: String, remainingAfter: Double): InvoiceReceipt {
    val payment = InvoicePayment(
        "p1", "2026-10-12", 1000.0, PaymentMethod.Cash, "Réf 77", "REC-2026-0001", "2026-10-12T09:00:00.000Z"
    )
    val invoice = Invoice(
        id = "f1",
        number = "FAC-2026-0001",
        status = InvoiceStatus.Issued,
        createdAt = "2026-10-10T09:00:00.000Z",
        updatedAt = "2026-10-10T09:00:00.000Z",
        issueDate = "2026-10-10",
        dueDate = "2026-11-09",
        clientName = "Awa Koné",
        clientPhone = "07 11",
        subject = subject,
        lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
        taxRate = 18.0,
        payments = listOf(payment)
    )
    return InvoiceReceipt("f1:p1", "REC-2026-0001", invoice, payment, remainingAfter)
}

class ReceiptDocumentTest {

    @Test
    fun acompteAvecResteAPayer() {
        val doc = receiptToDocument(receiptFor("Matériel", 1360.0))

        assertEquals("REÇU", doc.title)
        assertEquals("REC-2026-0001", doc.number)
        assertEquals("2026-10-12", doc.issueDate)
        assertNull(doc.dueLabel)
        assertNull(doc.dueDate)
        assertEquals("ACOMPTE", doc.stamp)
        assertEquals("Reçu de", doc.party.label)
        assertEquals("Paiement de la facture n° FAC-2026-0001", doc.subject)
        assertEquals(1, doc.lines.size)
        assertEquals("Paiement facture FAC-2026-0001 — Matériel", doc.lines[0].description)
        assertEquals(1000.0, doc.totals.total, 0.0)
        assertEquals(1000.0, doc.totals.balance, 0.0)
        assertEquals("Mode de paiement", doc.blocks[0].title)
        assertEquals("Espèces", doc.blocks[0].text)
        assertEquals("Référence", doc.blocks[1].title)
        assertEquals("Réf 77", doc.blocks[1].text)
        assertEquals(
            "Reste à payer sur la facture n° FAC-2026-0001 : " + formatMoney(1360.0) + ".",
            doc.blocks[2].text
        )
        assertTrue(doc.payments.isEmpty())
        assertNull(doc.balanceDue)
        assertEquals("Reçu la somme de", doc.amountInWordsLead)
        assertEquals(Pair("Signature du client", "Cachet et signature"), doc.signatures)
    }

    @Test
    fun factureSoldeeEtSansObjet() {
        val doc = receiptToDocument(receiptFor("", 0.0))

        assertEquals("SOLDÉ", doc.stamp)
        assertEquals("Paiement facture FAC-2026-0001", doc.lines[0].description)
        assertEquals("Facture n° FAC-2026-0001 entièrement réglée.", doc.blocks[2].text)
    }
}
