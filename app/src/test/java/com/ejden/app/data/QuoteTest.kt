package com.ejden.app.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val QT_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")

private class QuoteEnv {
    val store = InMemoryStore()
    val numbering = DocumentNumbering(store, now = { QT_NOW }, zone = ZoneOffset.UTC)
    val quotes = QuoteRepository(store, numbering, now = { QT_NOW }, zone = ZoneOffset.UTC)
}

private fun sampleQuote(
    id: String = "q1",
    createdAt: String = "2026-10-10T09:00:00.000Z",
    status: QuoteStatus = QuoteStatus.Draft,
    issueDate: String = "2026-10-10",
    validUntil: String = "2026-11-09"
) = Quote(
    id = id,
    number = "DEV-2026-0001",
    status = status,
    createdAt = createdAt,
    updatedAt = createdAt,
    issueDate = issueDate,
    validUntil = validUntil,
    clientName = "Awa Koné",
    clientPhone = "07 11",
    lines = listOf(DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0)),
    taxRate = 18.0
)

class QuoteTest {

    @Test
    fun enregistrerRemplacerEtTrier() {
        val e = QuoteEnv()
        val older = sampleQuote("q1", createdAt = "2026-10-10T09:00:00.000Z")
        val newer = sampleQuote("q2", createdAt = "2026-10-12T09:00:00.000Z")

        assertTrue(e.quotes.saveQuote(older))
        assertTrue(e.quotes.saveQuote(newer))
        assertEquals(listOf("q2", "q1"), e.quotes.getQuotes().map { it.id })

        // Même id : le devis est remplacé, pas dupliqué.
        assertTrue(e.quotes.saveQuote(older.copy(subject = "Mise à jour")))
        assertEquals(2, e.quotes.getQuotes().size)
        assertEquals("Mise à jour", e.quotes.getQuoteById("q1")!!.subject)

        // Un devis invalide est refusé.
        assertFalse(e.quotes.saveQuote(older.copy(clientName = "")))
        assertEquals(2360.0, e.quotes.getQuoteById("q1")!!.totals().total, 0.0)
    }

    @Test
    fun lectureRefuseLesDevisIncoherents() {
        val q = sampleQuote()
        assertNotNull(normalizeQuote(q.toJson()))
        assertEquals(q, normalizeQuote(q.toJson()))

        assertNull(normalizeQuote(q.toJson().put("lines", JSONArray())))
        assertNull(normalizeQuote(q.toJson().put("validUntil", "2026-13-40")))
        assertNull(normalizeQuote(q.toJson().put("clientName", "")))
        assertNull(normalizeQuote(q.toJson().put("status", "weird")))

        // Total au-dessus de la limite.
        val huge = q.copy(lines = listOf(DocumentLine(null, "X", "", 1_000_000.0, 50_000_000.0, 0.0)))
        assertNull(normalizeQuote(huge.toJson()))

        // Un e-mail invalide est effacé, TVA et acompte sont bornés.
        val cleaned = normalizeQuote(q.copy(clientEmail = "pas-un-mail", taxRate = 150.0, depositPct = -5.0).toJson())!!
        assertEquals("", cleaned.clientEmail)
        assertEquals(100.0, cleaned.taxRate, 0.0)
        assertEquals(0.0, cleaned.depositPct, 0.0)
    }

    @Test
    fun statutsEtExpiration() {
        val e = QuoteEnv()
        e.quotes.saveQuote(sampleQuote())

        assertTrue(e.quotes.setQuoteStatus("q1", QuoteStatus.Sent))
        val sent = e.quotes.getQuoteById("q1")!!
        assertEquals(QuoteStatus.Sent, sent.status)
        assertEquals("2026-10-15T12:00:00.000Z", sent.updatedAt)
        assertFalse(e.quotes.setQuoteStatus("zzz", QuoteStatus.Sent))

        val today = "2026-10-15"
        assertEquals(QuoteDisplayStatus.Expired, e.quotes.displayStatus(sampleQuote(validUntil = "2026-10-14"), today))
        // Le jour de la fin de validité, le devis est encore valable.
        assertEquals(QuoteDisplayStatus.Draft, e.quotes.displayStatus(sampleQuote(validUntil = "2026-10-15"), today))
        assertEquals(
            QuoteDisplayStatus.Expired,
            e.quotes.displayStatus(sampleQuote(status = QuoteStatus.Sent, validUntil = "2026-10-01"), today)
        )
        // Un devis accepté ou refusé n'expire pas.
        assertEquals(
            QuoteDisplayStatus.Accepted,
            e.quotes.displayStatus(sampleQuote(status = QuoteStatus.Accepted, validUntil = "2026-10-01"), today)
        )
        assertEquals(
            QuoteDisplayStatus.Refused,
            e.quotes.displayStatus(sampleQuote(status = QuoteStatus.Refused, validUntil = "2026-10-01"), today)
        )
    }

    @Test
    fun dupliquerUnDevis() {
        val e = QuoteEnv()
        e.quotes.saveQuote(sampleQuote(status = QuoteStatus.Accepted).copy(invoiceId = "inv9"))

        val copy = e.quotes.duplicateQuote("q1")!!
        assertNotEquals("q1", copy.id)
        assertEquals("DEV-2026-0001", copy.number)
        assertEquals(QuoteStatus.Draft, copy.status)
        assertNull(copy.invoiceId)
        assertEquals("2026-10-15", copy.issueDate)
        // Même durée de validité que l'original (30 jours).
        assertEquals("2026-11-14", copy.validUntil)
        assertEquals("2026-10-15T12:00:00.000Z", copy.createdAt)
        assertEquals(2, e.quotes.getQuotes().size)

        assertNull(e.quotes.duplicateQuote("zzz"))
    }

    @Test
    fun supprimerUnDevis() {
        val e = QuoteEnv()
        e.quotes.saveQuote(sampleQuote())

        assertTrue(e.quotes.deleteQuote("q1"))
        assertTrue(e.quotes.getQuotes().isEmpty())
        assertFalse(e.quotes.deleteQuote("q1"))
    }
}
