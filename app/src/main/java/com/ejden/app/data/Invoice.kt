package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class InvoiceStatus(val id: String) {
    Draft("draft"),
    Issued("issued"),
    Cancelled("cancelled");

    companion object {
        fun fromId(value: Any?): InvoiceStatus? = entries.firstOrNull { it.id == value }
    }
}

/** Statut affiché : calculé à partir du statut enregistré, des paiements et de l'échéance. */
enum class InvoiceDisplayStatus(val label: String) {
    Draft("Brouillon"),
    Unpaid("À payer"),
    Partial("Partiellement payée"),
    Overdue("En retard"),
    Paid("Payée"),
    Cancelled("Annulée")
}

enum class InvoicePaymentResult { Ok, NotFound, NotIssued, TooMuch, Invalid, Error }

data class InvoicePayment(
    val id: String,
    val date: String,
    val amount: Double,
    val method: PaymentMethod,
    val note: String,
    /** Numéro du reçu (REC-AAAA-0001). Vide pour les anciens paiements. */
    val receiptNumber: String,
    /** Moment de l'enregistrement (sert à l'heure dans la caisse). */
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("date", date)
        put("amount", amount)
        put("method", method.id)
        put("note", note)
        put("receiptNumber", receiptNumber)
        put("createdAt", createdAt)
    }
}

fun normalizeInvoicePayment(value: Any?): InvoicePayment? {
    val p = value as? JSONObject ?: return null
    val id = p.opt("id") as? String
    val amount = cleanAmount(p.opt("amount"))
    val date = p.opt("date") as? String
    val method = PaymentMethod.fromId(p.opt("method"))

    if (id == null || id.isEmpty() || id.length > 100 || amount == null ||
        date == null || !isValidDay(date) ||
        method == null || method !in CREDIT_PAYMENT_METHODS
    ) {
        return null
    }

    return InvoicePayment(
        id = id,
        date = date,
        amount = amount,
        method = method,
        note = cleanLabel(p.opt("note"), DocLimits.SHORT_MAX),
        receiptNumber = cleanLabel(p.opt("receiptNumber"), 30),
        createdAt = validDate(p.opt("createdAt")) ?: (date + "T12:00:00.000Z")
    )
}

data class Invoice(
    val id: String,
    /** Vide tant que la facture est un brouillon. */
    val number: String,
    val status: InvoiceStatus,
    val createdAt: String,
    val updatedAt: String,
    val issueDate: String,
    val dueDate: String,
    val quoteId: String? = null,
    val quoteNumber: String = "",
    val clientId: String? = null,
    val clientName: String,
    val clientPhone: String = "",
    val clientEmail: String = "",
    val clientAddress: String = "",
    val subject: String = "",
    val lines: List<DocumentLine>,
    val discount: Double = 0.0,
    val taxRate: Double = 0.0,
    val paymentTerms: String = "",
    val notes: String = "",
    val terms: String = "",
    val payments: List<InvoicePayment> = emptyList()
) {
    fun totals(): DocumentTotals = computeDocumentTotals(lines, discount, taxRate, 0.0)

    fun paid(): Double = payments.sumOf { it.amount }

    fun remaining(): Double = maxOf(0.0, totals().total - paid())

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("number", number)
        put("status", status.id)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("issueDate", issueDate)
        put("dueDate", dueDate)
        put("quoteId", quoteId ?: JSONObject.NULL)
        put("quoteNumber", quoteNumber)
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
        put("paymentTerms", paymentTerms)
        put("notes", notes)
        put("terms", terms)
        val paymentArray = JSONArray()
        for (payment in payments) paymentArray.put(payment.toJson())
        put("payments", paymentArray)
    }
}

private fun invoiceKeyOrNull(value: Any?): String? =
    (value as? String)?.takeIf { it.isNotEmpty() && it.length <= 100 }

/** Nombre borné à [0, max], arrondi à 2 décimales ; 0 si ce n'est pas un nombre. */
private fun invoiceNumberOrZero(value: Any?, max: Double): Double {
    val n = finiteNumber(value) ?: return 0.0
    return minOf(max, maxOf(0.0, Math.round(n * 100) / 100.0))
}

fun normalizeInvoice(value: Any?): Invoice? {
    val v = value as? JSONObject ?: return null
    val id = v.opt("id") as? String
    val createdAt = validDate(v.opt("createdAt"))
    val updatedAt = validDate(v.opt("updatedAt")) ?: createdAt
    val clientName = cleanLabel(v.opt("clientName"), DocLimits.SHORT_MAX)
    val clientPhone = cleanLabel(v.opt("clientPhone"), ModuleLimits.PHONE_MAX)
    val clientEmail = cleanDocText(v.opt("clientEmail"), DocLimits.SHORT_MAX)
    val number = cleanLabel(v.opt("number"), 30)
    val status = InvoiceStatus.fromId(v.opt("status"))
    val rawLines = v.opt("lines") as? JSONArray

    if (id == null || id.isEmpty() || id.length > 100 ||
        status == null || (status != InvoiceStatus.Draft && number.isEmpty()) ||
        createdAt == null || updatedAt == null ||
        !isValidDay(v.opt("issueDate")) || !isValidDay(v.opt("dueDate")) ||
        clientName.isEmpty() || !isValidPhone(clientPhone) ||
        rawLines == null || rawLines.length() == 0 || rawLines.length() > DocumentLimits.LINES_MAX
    ) {
        return null
    }

    val lines = ArrayList<DocumentLine>()
    for (index in 0 until rawLines.length()) {
        val line = normalizeDocumentLine(rawLines.opt(index)) ?: return null
        lines.add(line)
    }

    // Un paiement illisible est ignoré (comme dans la version HTML).
    val payments = ArrayList<InvoicePayment>()
    val rawPayments = v.opt("payments") as? JSONArray
    if (rawPayments != null) {
        for (index in 0 until rawPayments.length()) {
            val payment = normalizeInvoicePayment(rawPayments.opt(index))
            if (payment != null) payments.add(payment)
        }
    }

    val discount = Math.round(invoiceNumberOrZero(v.opt("discount"), ModuleLimits.AMOUNT_MAX)).toDouble()
    val taxRate = invoiceNumberOrZero(v.opt("taxRate"), 100.0)

    if (computeDocumentTotals(lines, discount, taxRate, 0.0).total > DocumentLimits.TOTAL_MAX) return null

    return Invoice(
        id = id,
        number = number,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        issueDate = v.opt("issueDate") as String,
        dueDate = v.opt("dueDate") as String,
        quoteId = invoiceKeyOrNull(v.opt("quoteId")),
        quoteNumber = cleanLabel(v.opt("quoteNumber"), 30),
        clientId = invoiceKeyOrNull(v.opt("clientId")),
        clientName = clientName,
        clientPhone = clientPhone,
        clientEmail = if (isValidEmail(clientEmail)) clientEmail else "",
        clientAddress = cleanDocText(v.opt("clientAddress"), DocLimits.ADDRESS_MAX),
        subject = cleanLabel(v.opt("subject"), DocumentLimits.DESCRIPTION_MAX),
        lines = lines,
        discount = discount,
        taxRate = taxRate,
        paymentTerms = cleanDocText(v.opt("paymentTerms"), DocLimits.ADDRESS_MAX),
        notes = cleanDocText(v.opt("notes"), DocLimits.LONG_MAX),
        terms = cleanDocText(v.opt("terms"), DocLimits.LONG_MAX),
        payments = payments
    )
}

/** Reçu : un par paiement d'une facture émise. */
data class InvoiceReceipt(
    /** « idFacture:idPaiement » */
    val key: String,
    val number: String,
    val invoice: Invoice,
    val payment: InvoicePayment,
    /** Reste à payer sur la facture juste après ce paiement. */
    val remainingAfter: Double
)

data class InvoiceCollected(val amount: Double, val count: Int)

data class InvoiceReceivable(
    val total: Double,
    val count: Int,
    val overdueTotal: Double,
    val overdueCount: Int
)

class InvoiceRepository(
    private val store: KeyValueStore,
    private val numbering: DocumentNumbering,
    private val quotes: QuoteRepository,
    private val documents: DocumentRepository,
    private val now: () -> Instant = { Instant.now() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object {
        const val KEY = "ejden_invoices"
    }

    /** Aujourd'hui (AAAA-MM-JJ, heure locale). */
    fun today(): String = now().atZone(zone).toLocalDate().toString()

    /** Du plus récent au plus ancien. */
    fun getInvoices(): List<Invoice> =
        readList(store, KEY, ::normalizeInvoice).sortedByDescending { it.createdAt }

    fun getInvoiceById(invoiceId: String): Invoice? = getInvoices().firstOrNull { it.id == invoiceId }

    private fun write(invoices: List<Invoice>): Boolean =
        writeList(store, KEY, invoices.map { it.toJson() })

    /** Ajoute la facture, ou la remplace s'il existe déjà (même id). */
    fun saveInvoice(invoice: Invoice): Boolean {
        val clean = normalizeInvoice(invoice.toJson()) ?: return false
        val invoices = getInvoices().toMutableList()
        val index = invoices.indexOfFirst { it.id == clean.id }
        if (index == -1) invoices.add(clean) else invoices[index] = clean
        return write(invoices)
    }

    /** Seuls les brouillons se suppriment (une facture émise s'annule). */
    fun deleteInvoice(invoiceId: String): Boolean {
        val invoices = getInvoices()
        val target = invoices.firstOrNull { it.id == invoiceId }
        if (target == null || target.status != InvoiceStatus.Draft) return false
        return write(invoices.filter { it.id != invoiceId })
    }

    fun displayStatus(invoice: Invoice, day: String = today()): InvoiceDisplayStatus {
        if (invoice.status == InvoiceStatus.Draft) return InvoiceDisplayStatus.Draft
        if (invoice.status == InvoiceStatus.Cancelled) return InvoiceDisplayStatus.Cancelled
        if (invoice.remaining() == 0.0) return InvoiceDisplayStatus.Paid
        if (invoice.dueDate < day) return InvoiceDisplayStatus.Overdue
        return if (invoice.paid() > 0.0) InvoiceDisplayStatus.Partial else InvoiceDisplayStatus.Unpaid
    }

    /** Émet un brouillon : attribue le numéro définitif (FAC-AAAA-0001). */
    fun issueInvoice(invoiceId: String): Invoice? {
        val invoice = getInvoiceById(invoiceId) ?: return null
        if (invoice.status != InvoiceStatus.Draft) return null

        val issued = invoice.copy(
            status = InvoiceStatus.Issued,
            number = numbering.reserve(DocumentType.Invoice, invoice.issueDate),
            updatedAt = toIso(now())
        )
        return if (saveInvoice(issued)) issued else null
    }

    /**
     * Une facture déjà payée en partie ne s'annule pas : il faut d'abord retirer
     * les paiements, sinon la caisse et la facture ne seraient plus d'accord.
     */
    fun cancelInvoice(invoiceId: String): Boolean {
        val invoice = getInvoiceById(invoiceId) ?: return false
        if (invoice.status != InvoiceStatus.Issued || invoice.payments.isNotEmpty()) return false
        return saveInvoice(invoice.copy(status = InvoiceStatus.Cancelled, updatedAt = toIso(now())))
    }

    fun addPayment(
        invoiceId: String,
        amount: Double,
        method: PaymentMethod,
        date: String,
        note: String
    ): InvoicePaymentResult {
        val invoice = getInvoiceById(invoiceId) ?: return InvoicePaymentResult.NotFound
        if (invoice.status != InvoiceStatus.Issued) return InvoicePaymentResult.NotIssued

        val raw = JSONObject().apply {
            put("id", newId())
            put("date", date)
            put("amount", amount)
            put("method", method.id)
            put("note", note)
            put("createdAt", toIso(now()))
        }
        val clean = normalizeInvoicePayment(raw) ?: return InvoicePaymentResult.Invalid
        if (clean.amount > invoice.remaining()) return InvoicePaymentResult.TooMuch

        val withReceipt = clean.copy(receiptNumber = numbering.reserve(DocumentType.Receipt, date))
        val saved = saveInvoice(
            invoice.copy(payments = invoice.payments + withReceipt, updatedAt = toIso(now()))
        )
        return if (saved) InvoicePaymentResult.Ok else InvoicePaymentResult.Error
    }

    fun removePayment(invoiceId: String, paymentId: String): Boolean {
        val invoice = getInvoiceById(invoiceId) ?: return false
        if (invoice.status != InvoiceStatus.Issued) return false
        return saveInvoice(invoice.copy(payments = invoice.payments.filter { it.id != paymentId }))
    }

    /** Copie d'une facture : nouveau brouillon sans numéro ni paiements, dates d'aujourd'hui. */
    fun duplicateInvoice(invoiceId: String): Invoice? {
        val source = getInvoiceById(invoiceId) ?: return null
        val day = today()
        val stamp = toIso(now())

        val copy = source.copy(
            id = newId(),
            number = "",
            status = InvoiceStatus.Draft,
            createdAt = stamp,
            updatedAt = stamp,
            issueDate = day,
            dueDate = addDaysToDay(day, documents.getSettings().defaultDueDays),
            quoteId = null,
            quoteNumber = "",
            payments = emptyList()
        )
        return if (saveInvoice(copy)) copy else null
    }

    /** Crée un brouillon de facture à partir d'un devis accepté (une seule fois). */
    fun createInvoiceFromQuote(quoteId: String): Invoice? {
        val quote = quotes.getQuoteById(quoteId) ?: return null
        if (quote.status != QuoteStatus.Accepted || quote.invoiceId != null) return null

        val day = today()
        val stamp = toIso(now())
        val settings = documents.getSettings()
        val invoice = Invoice(
            id = newId(),
            number = "",
            status = InvoiceStatus.Draft,
            createdAt = stamp,
            updatedAt = stamp,
            issueDate = day,
            dueDate = addDaysToDay(day, settings.defaultDueDays),
            quoteId = quote.id,
            quoteNumber = quote.number,
            clientId = quote.clientId,
            clientName = quote.clientName,
            clientPhone = quote.clientPhone,
            clientEmail = quote.clientEmail,
            clientAddress = quote.clientAddress,
            subject = quote.subject,
            lines = quote.lines,
            discount = quote.discount,
            taxRate = quote.taxRate,
            paymentTerms = quote.paymentTerms,
            notes = quote.notes,
            terms = settings.invoiceTerms
        )

        if (!saveInvoice(invoice)) return null
        return if (quotes.saveQuote(quote.copy(invoiceId = invoice.id))) invoice else null
    }

    /** Moment d'un paiement (pour la caisse et les finances) : le jour saisi fait foi. */
    fun paymentTime(payment: InvoicePayment): String {
        val savedMillis = parseDateMillis(payment.createdAt)
        if (savedMillis != null) {
            val savedDay = Instant.ofEpochMilli(savedMillis).atZone(zone).toLocalDate().toString()
            if (savedDay == payment.date) return toIso(Instant.ofEpochMilli(savedMillis))
        }
        return toIso(LocalDate.parse(payment.date).atTime(12, 0).atZone(zone).toInstant())
    }

    /** Paiements de factures encaissés sur une période. */
    fun collectedBetween(start: Instant, end: Instant): InvoiceCollected {
        val from = start.toEpochMilli()
        val to = end.toEpochMilli()
        var amount = 0.0
        var count = 0

        for (invoice in getInvoices()) {
            if (invoice.status != InvoiceStatus.Issued) continue
            for (payment in invoice.payments) {
                val at = parseDateMillis(paymentTime(payment)) ?: continue
                if (at in from..to) {
                    amount += payment.amount
                    count += 1
                }
            }
        }
        return InvoiceCollected(amount, count)
    }

    /** Factures émises restant à encaisser (dont celles en retard). */
    fun receivable(day: String = today()): InvoiceReceivable {
        var total = 0.0
        var count = 0
        var overdueTotal = 0.0
        var overdueCount = 0

        for (invoice in getInvoices()) {
            if (invoice.status != InvoiceStatus.Issued) continue
            val remaining = invoice.remaining()
            if (remaining == 0.0) continue

            total += remaining
            count += 1
            if (invoice.dueDate < day) {
                overdueTotal += remaining
                overdueCount += 1
            }
        }
        return InvoiceReceivable(total, count, overdueTotal, overdueCount)
    }

    /** Tous les reçus (un par paiement de facture émise), le plus récent d'abord. */
    fun getReceipts(): List<InvoiceReceipt> {
        val receipts = ArrayList<InvoiceReceipt>()

        for (invoice in getInvoices()) {
            if (invoice.status != InvoiceStatus.Issued) continue

            val ordered = invoice.payments.sortedWith(
                compareBy<InvoicePayment>({ it.date }, { it.createdAt })
            )
            val total = invoice.totals().total
            var paid = 0.0

            ordered.forEachIndexed { index, payment ->
                paid += payment.amount
                receipts.add(
                    InvoiceReceipt(
                        key = invoice.id + ":" + payment.id,
                        number = payment.receiptNumber.ifEmpty { "REC-" + invoice.number + "-" + (index + 1) },
                        invoice = invoice,
                        payment = payment,
                        remainingAfter = maxOf(0.0, total - paid)
                    )
                )
            }
        }

        return receipts.sortedWith(
            compareByDescending<InvoiceReceipt> { it.payment.date }.thenByDescending { it.payment.createdAt }
        )
    }

    fun getReceiptByKey(key: String): InvoiceReceipt? = getReceipts().firstOrNull { it.key == key }
}
