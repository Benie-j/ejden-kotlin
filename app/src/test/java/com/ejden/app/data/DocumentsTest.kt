package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val DOC_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")

class DocumentsTest {

    @Test
    fun numerotationParTypeEtParAnnee() {
        val store = InMemoryStore()
        val numbering = DocumentNumbering(store, now = { DOC_NOW }, zone = ZoneOffset.UTC)

        assertEquals("DEV-2026-0001", numbering.reserve(DocumentType.Quote, "2026-10-01"))
        assertEquals("DEV-2026-0002", numbering.reserve(DocumentType.Quote, "2026-10-02"))
        assertEquals("FAC-2026-0001", numbering.reserve(DocumentType.Invoice, "2026-10-02"))
        assertEquals("REC-2026-0001", numbering.reserve(DocumentType.Receipt, "2026-10-02"))
        assertEquals("DEV-2027-0001", numbering.reserve(DocumentType.Quote, "2027-03-01"))
        // Date invalide : l'année du jour est utilisée.
        assertEquals("DEV-2026-0003", numbering.reserve(DocumentType.Quote, "n'importe quoi"))

        // Les compteurs sont enregistrés : un autre objet continue la suite.
        val again = DocumentNumbering(store, now = { DOC_NOW }, zone = ZoneOffset.UTC)
        assertEquals("DEV-2026-0004", again.reserve(DocumentType.Quote, "2026-12-31"))
    }

    @Test
    fun totauxDUnDocument() {
        val lines = listOf(
            DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0),
            DocumentLine("p2", "Huile", "bidon", 3.0, 500.0, 10.0)
        )
        assertEquals(2000.0, documentLineTotal(lines[0]), 0.0)
        assertEquals(1350.0, documentLineTotal(lines[1]), 0.0)

        val totals = computeDocumentTotals(lines, discount = 350.0, taxRate = 18.0, depositPct = 30.0)
        assertEquals(3350.0, totals.subtotal, 0.0)
        assertEquals(350.0, totals.discount, 0.0)
        assertEquals(3000.0, totals.net, 0.0)
        assertEquals(540.0, totals.tax, 0.0)
        assertEquals(3540.0, totals.total, 0.0)
        assertEquals(1062.0, totals.deposit, 0.0)
        assertEquals(2478.0, totals.balance, 0.0)

        // La remise ne peut pas dépasser le sous-total.
        val capped = computeDocumentTotals(lines, discount = 99999.0, taxRate = 18.0, depositPct = 0.0)
        assertEquals(3350.0, capped.discount, 0.0)
        assertEquals(0.0, capped.net, 0.0)
        assertEquals(0.0, capped.total, 0.0)
    }

    @Test
    fun lectureDUneLigne() {
        val ok = DocumentLine(null, "  Riz   25 kg ", "sac", 2.346, 1000.4, 150.0).toJson()
        val line = normalizeDocumentLine(ok)!!
        assertEquals("Riz 25 kg", line.description)
        assertEquals(2.35, line.quantity, 0.0)
        assertEquals(1000.0, line.unitPrice, 0.0)
        // La remise est bornée à 100 %.
        assertEquals(100.0, line.discountPct, 0.0)

        assertNotNull(normalizeDocumentLine(DocumentLine("p1", "A", "", 1.0, 0.0, 0.0).toJson()))
        assertNull(normalizeDocumentLine(DocumentLine(null, "", "", 1.0, 10.0, 0.0).toJson()))
        assertNull(normalizeDocumentLine(DocumentLine(null, "A", "", 0.0, 10.0, 0.0).toJson()))
        assertNull(normalizeDocumentLine(DocumentLine(null, "A", "", 1.0, -1.0, 0.0).toJson()))
        assertNull(normalizeDocumentLine(DocumentLine(null, "A", "", 2_000_000.0, 10.0, 0.0).toJson()))
        assertNull(normalizeDocumentLine(JSONObject().put("description", "A")))
    }

    @Test
    fun ajouterDesJours() {
        assertEquals("2026-11-09", addDaysToDay("2026-10-10", 30))
        assertEquals("2027-01-01", addDaysToDay("2026-12-31", 1))
    }
}
