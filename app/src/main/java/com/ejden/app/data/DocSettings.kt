package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener

object DocLimits {
    const val SHORT_MAX = 80
    const val ADDRESS_MAX = 160
    const val LONG_MAX = 600
}

const val DEFAULT_QUOTE_TERMS =
    "Devis valable pour la durée indiquée. Les prix sont exprimés en FCFA. " +
        "La commande est ferme après acceptation écrite (signature) et, le cas échéant, " +
        "versement de l'acompte."

const val DEFAULT_INVOICE_TERMS =
    "Paiement à réception ou avant la date d'échéance. En cas de retard, des " +
        "pénalités pourront être appliquées. Marchandises vendues non reprises ni échangées sauf accord écrit."

private val DOC_CONTROL = Regex("[\\u0000-\\u0009\\u000B-\\u001F\\u007F]")

/** Texte sûr, plusieurs lignes permises : sans caractères de contrôle. */
fun cleanDocText(value: Any?, max: Int): String =
    if (value is String) {
        value.replace("\r\n", "\n").replace('\r', '\n').replace(DOC_CONTROL, " ").jsTrim().take(max)
    } else {
        ""
    }

private val EMAIL_PATTERN = Regex(
    "[^\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@<>\"']+@" +
        "[^\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@<>\"']+\\." +
        "[^\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF@<>\"']+"
)

fun isValidEmail(value: String): Boolean =
    value == "" || (value.length <= DocLimits.SHORT_MAX && EMAIL_PATTERN.matches(value))

data class DocumentSettings(
    val address: String,
    val email: String,
    val website: String,
    /** NIF / IFU. */
    val taxId: String,
    /** RCCM. */
    val registry: String,
    val paymentInfo: String,
    val logo: String?,
    val defaultTaxRate: Double,
    val defaultValidityDays: Int,
    val quoteTerms: String,
    val defaultDueDays: Int,
    val invoiceTerms: String,
    val footer: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("address", address)
        put("email", email)
        put("website", website)
        put("taxId", taxId)
        put("registry", registry)
        put("paymentInfo", paymentInfo)
        put("logo", logo ?: JSONObject.NULL)
        put("defaultTaxRate", defaultTaxRate)
        put("defaultValidityDays", defaultValidityDays)
        put("quoteTerms", quoteTerms)
        put("defaultDueDays", defaultDueDays)
        put("invoiceTerms", invoiceTerms)
        put("footer", footer)
    }
}

fun normalizeDocumentSettings(value: Any?): DocumentSettings {
    val data = value as? JSONObject ?: JSONObject()

    val email = cleanDocText(data.opt("email"), DocLimits.SHORT_MAX)
    val rawRate = finiteNumber(data.opt("defaultTaxRate"))
    val rate = if (rawRate != null) minOf(100.0, maxOf(0.0, Math.round(rawRate * 100) / 100.0)) else 0.0
    val rawDays = finiteNumber(data.opt("defaultValidityDays"))
    val days = if (rawDays != null) minOf(365L, maxOf(1L, Math.round(rawDays))).toInt() else 30
    val rawDue = finiteNumber(data.opt("defaultDueDays"))
    val due = if (rawDue != null) minOf(365L, maxOf(0L, Math.round(rawDue))).toInt() else 30

    return DocumentSettings(
        address = cleanDocText(data.opt("address"), DocLimits.ADDRESS_MAX),
        email = if (isValidEmail(email)) email else "",
        website = cleanDocText(data.opt("website"), DocLimits.SHORT_MAX),
        taxId = cleanDocText(data.opt("taxId"), 40),
        registry = cleanDocText(data.opt("registry"), 40),
        paymentInfo = cleanDocText(data.opt("paymentInfo"), DocLimits.LONG_MAX),
        logo = (data.opt("logo") as? String)?.takeIf { isSafePhoto(it) },
        defaultTaxRate = rate,
        defaultValidityDays = days,
        quoteTerms = if (data.opt("quoteTerms") is String) {
            cleanDocText(data.opt("quoteTerms"), DocLimits.LONG_MAX)
        } else {
            DEFAULT_QUOTE_TERMS
        },
        defaultDueDays = due,
        invoiceTerms = if (data.opt("invoiceTerms") is String) {
            cleanDocText(data.opt("invoiceTerms"), DocLimits.LONG_MAX)
        } else {
            DEFAULT_INVOICE_TERMS
        },
        footer = cleanDocText(data.opt("footer"), DocLimits.ADDRESS_MAX)
    )
}

class DocumentRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_doc_settings"
    }

    fun getSettings(): DocumentSettings {
        val stored = store.get(KEY)
        if (stored != null && stored.isNotEmpty()) {
            try {
                return normalizeDocumentSettings(JSONTokener(stored).nextValue())
            } catch (e: Exception) {
                // données illisibles : valeurs par défaut
            }
        }
        return normalizeDocumentSettings(JSONObject())
    }

    fun saveSettings(settings: DocumentSettings): Boolean =
        store.set(KEY, normalizeDocumentSettings(settings.toJson()).toJson().toString())
}
