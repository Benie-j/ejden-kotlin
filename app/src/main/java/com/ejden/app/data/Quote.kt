package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class QuoteStatus(val id: String) {
    Draft("draft"),
    Sent("sent"),
    Accepted("accepted"),
    Refused("refused");

    companion object {
        fun fromId(value: Any?): QuoteStatus? = entries.firstOrNull { it.id == value }
    }
}

/** Statut affiché : « Expiré » s'ajoute aux quatre statuts enregistrés. */
enum class QuoteDisplayStatus(val label: String) {
    Draft("Brouillon"),
    Sent("Envoyé"),
    Accepted("Accepté"),
    Refused("Refusé"),
    Expired("Expiré")
}

data class Quote(
    val id: String,
    val number: String,
    val status: QuoteStatus,
    val createdAt: String,
    val updatedAt: String,
    val issueDate: String,
    val validUntil: String,
    /** Client : copie des informations au moment du devis (le devis reste identique si la fiche change). */
    val clientId: String? = null,
    val clientName: String,
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val subject: String = "",
    val lines: List<DocumentLine>,
    /** Remise globale en FCFA, TVA en %, acompte demandé en %. */
    val discount: Double = 0.0,
    val taxRate: Double = 0.0,
    val depositPct: Double = 0.0,
    val deliveryTerms: String = "",
    val paymentTerms: String = "",
    val notes: String = "",
    val terms: String = "",
    /** Facture créée à partir de ce devis (une seule). */
    val invoiceId: String? = null
) {
    fun totals(): DocumentTotals = computeDocumentTotals(lines, discount, taxRate, depositPct)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("status", status.id)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("issueDate", issueDate)
        put("validUntil", validUntil)
        put("clientId", clientId ?: JSONObject.NULL)
        put("clientName", clientName)
        put("clientPhone", clientPhone)
        put("clientEmail", clientEmail)
        put("clientAddress", clientAddress)
        put("subject", subject)
        val array = JSONArray()
        for (line in lines) array.put(line.toJson())
        put("lines", array)
        put("discount", discount)
        put("taxRate", taxRate)
        put("depositPct", depositPct)
        put("deliveryTerms", deliveryTerms)
        put("paymentTerms", paymentTerms)
        put("notes", notes)
        put("terms", terms)
        put("invoiceId", invoiceId ?: JSONObject.NULL)
    }
}

private fun shortKeyOrNull(value: Any?): String? =
    (value as? String)?.takeIf { it.isNotEmpty() && it.length <= 100 }

/** Nombre borné, arrondi à 2 décimales ; valeur de secours si ce n'est pas un nombre. */
private fun clampedNumber(value: Any?, min: Double, max: Double, fallback: Double): Double {
    val n = finiteNumber(value) ?: return fallback
    return minOf(max, maxOf(min, Math.round(n * 100) / 100.0))
}

fun normalizeQuote(value: Any?): Quote? {
    val q = value as? JSONObject ?: return null
    val id = q.opt("id") as? String
    val createdAt = validDate(q.opt("createdAt"))
    val updatedAt = validDate(q.opt("updatedAt")) ?: createdAt
    val clientName = cleanLabel(q.opt("clientName"), DocLimits.SHORT_MAX)
    val clientPhone = cleanLabel(q.opt("clientPhone"), ModuleLimits.PHONE_MAX)
    val clientEmail = cleanDocText(q.opt("clientEmail"), DocLimits.SHORT_MAX)
    val number = cleanLabel(q.opt("number"), 30)
    val status = QuoteStatus.fromId(q.opt("status"))
    val rawLines = q.opt("lines") as? JSONArray

    if (id == null || id.isEmpty() || id.length > 100 || number.isEmpty() ||
        createdAt == null || updatedAt == null ||
        !isValidDay(q.opt("issueDate")) || !isValidDay(q.opt("validUntil")) ||
        clientName.isEmpty() || !isValidPhone(clientPhone) ||
        rawLines == null || rawLines.length() == 0 || rawLines.length() > DocumentLimits.LINES_MAX ||
        status == null
    ) {
        return null
    }

    val lines = ArrayList<DocumentLine>()
    for (index in 0 until rawLines.length()) {
        val line = normalizeDocumentLine(rawLines.opt(index)) ?: return null
        lines.add(line)
    }

    val discount = Math.round(clampedNumber(q.opt("discount"), 0.0, ModuleLimits.AMOUNT_MAX, 0.0)).toDouble()
    val taxRate = clampedNumber(q.opt("taxRate"), 0.0, 100.0, 0.0)
    val depositPct = clampedNumber(q.opt("depositPct"), 0.0, 100.0, 0.0)

    if (computeDocumentTotals(lines, discount, taxRate, depositPct).total > DocumentLimits.TOTAL_MAX) return null

    return Quote(
        id = id,
        number = number,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        issueDate = q.opt("issueDate") as String,
        validUntil = q.opt("validUntil") as String,
        clientId = shortKeyOrNull(q.opt("clientId")),
        clientName = clientName,
        clientPhone = clientPhone,
        clientEmail = if (isValidEmail(clientEmail)) clientEmail else "",
        clientAddress = cleanDocText(q.opt("clientAddress"), DocLimits.ADDRESS_MAX),
        subject = cleanLabel(q.opt("subject"), DocumentLimits.DESCRIPTION_MAX),
        lines = lines,
        discount = discount,
        taxRate = taxRate,
        depositPct = depositPct,
        deliveryTerms = cleanDocText(q.opt("deliveryTerms"), DocLimits.ADDRESS_MAX),
        paymentTerms = cleanDocText(q.opt("paymentTerms"), DocLimits.ADDRESS_MAX),
        notes = cleanDocText(q.opt("notes"), DocLimits.LONG_MAX),
        terms = cleanDocText(q.opt("terms"), DocLimits.LONG_MAX),
        invoiceId = shortKeyOrNull(q.opt("invoiceId"))
    )
}

class QuoteRepository(
    private val store: KeyValueStore,
    private val numbering: DocumentNumbering,
    private val now: () -> Instant = { Instant.now() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object {
        const val KEY = "ejden_quotes"
    }

    /** Aujourd'hui (AAAA-MM-JJ, heure locale). */
    fun today(): String = now().atZone(zone).toLocalDate().toString()

    /** Réserve le numéro d'un nouveau devis (DEV-2026-0001). */
    fun nextNumber(day: String): String = numbering.reserve(DocumentType.Quote, day)

    /** Du plus récent au plus ancien. */
    fun getQuotes(): List<Quote> =
        readList(store, KEY, ::normalizeQuote).sortedByDescending { it.createdAt }

    fun getQuoteById(quoteId: String): Quote? = getQuotes().firstOrNull { it.id == quoteId }

    private fun write(quotes: List<Quote>): Boolean =
        writeList(store, KEY, quotes.map { it.toJson() })

    /** Ajoute le devis, ou le remplace s'il existe déjà (même id). */
    fun saveQuote(quote: Quote): Boolean {
        val clean = normalizeQuote(quote.toJson()) ?: return false
        val quotes = getQuotes().toMutableList()
        val index = quotes.indexOfFirst { it.id == clean.id }
        if (index == -1) quotes.add(clean) else quotes[index] = clean
        return write(quotes)
    }

    fun deleteQuote(quoteId: String): Boolean {
        val quotes = getQuotes()
        val remaining = quotes.filter { it.id != quoteId }
        return if (remaining.size == quotes.size) false else write(remaining)
    }

    fun setQuoteStatus(quoteId: String, status: QuoteStatus): Boolean {
        val quote = getQuoteById(quoteId) ?: return false
        return saveQuote(quote.copy(status = status, updatedAt = toIso(now())))
    }

    /** Copie d'un devis : nouveau numéro, brouillon, dates d'aujourd'hui. */
    fun duplicateQuote(quoteId: String): Quote? {
        val source = getQuoteById(quoteId) ?: return null
        val todayDay = today()
        val span = maxOf(
            1L,
            ChronoUnit.DAYS.between(LocalDate.parse(source.issueDate), LocalDate.parse(source.validUntil))
        ).toInt()
        val stamp = toIso(now())

        val copy = source.copy(
            id = newId(),
            number = nextNumber(todayDay),
            status = QuoteStatus.Draft,
            invoiceId = null,
            createdAt = stamp,
            updatedAt = stamp,
            issueDate = todayDay,
            validUntil = addDaysToDay(todayDay, span)
        )
        return if (saveQuote(copy)) copy else null
    }

    /** Un devis en brouillon ou envoyé dont la validité est dépassée est « expiré ». */
    fun displayStatus(quote: Quote, day: String = today()): QuoteDisplayStatus {
        val open = quote.status == QuoteStatus.Draft || quote.status == QuoteStatus.Sent
        if (open && quote.validUntil < day) return QuoteDisplayStatus.Expired
        return when (quote.status) {
            QuoteStatus.Draft -> QuoteDisplayStatus.Draft
            QuoteStatus.Sent -> QuoteDisplayStatus.Sent
            QuoteStatus.Accepted -> QuoteDisplayStatus.Accepted
            QuoteStatus.Refused -> QuoteDisplayStatus.Refused
        }
    }
}
