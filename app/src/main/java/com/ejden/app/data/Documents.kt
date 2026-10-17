package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

/** Types de documents et leur préfixe de numéro (DEV-2026-0001). */
enum class DocumentType(val id: String, val prefix: String) {
    Quote("quote", "DEV"),
    Invoice("invoice", "FAC"),
    Receipt("receipt", "REC")
}

object DocumentLimits {
    const val LINES_MAX = 60
    const val DESCRIPTION_MAX = 120
    const val UNIT_MAX = 12
    const val TOTAL_MAX = 100_000_000_000.0
}

/** Ajoute des jours à une date AAAA-MM-JJ. */
fun addDaysToDay(day: String, days: Int): String = LocalDate.parse(day).plusDays(days.toLong()).toString()

/**
 * Numérotation : un compteur par type et par année (DEV-2026-0001, DEV-2026-0002...).
 * Les compteurs sont dans la sauvegarde (ejden_doc_counters).
 */
class DocumentNumbering(
    private val store: KeyValueStore,
    private val now: () -> Instant = { Instant.now() },
    private val zone: ZoneId = ZoneId.systemDefault()
) {
    companion object {
        const val KEY = "ejden_doc_counters"
    }

    private fun readCounters(): MutableMap<String, Int> {
        val result = LinkedHashMap<String, Int>()
        val stored = store.get(KEY)
        if (stored.isNullOrEmpty()) return result

        try {
            val parsed = JSONTokener(stored).nextValue()
            if (parsed is JSONObject) {
                for (name in parsed.keys()) {
                    val count = finiteNumber(parsed.opt(name))
                    if (count != null && count == floor(count) && count >= 0.0) {
                        result[name] = count.toInt()
                    }
                }
            }
        } catch (e: Exception) {
            // compteurs illisibles : on repart de zéro
        }
        return result
    }

    /** Réserve le numéro suivant d'un type de document pour l'année de la date. */
    fun reserve(type: DocumentType, day: String): String {
        val year = if (isValidDay(day)) day.substring(0, 4) else now().atZone(zone).year.toString()
        val key = type.id + "-" + year
        val counters = readCounters()
        val next = (counters[key] ?: 0) + 1
        counters[key] = next

        val json = JSONObject()
        for ((name, count) in counters) json.put(name, count)
        store.set(KEY, json.toString())

        return type.prefix + "-" + year + "-" + next.toString().padStart(4, '0')
    }
}

/* ---------- Lignes et totaux (communs devis / factures) ---------- */

data class DocumentLine(
    val productId: String?,
    val description: String,
    val unit: String,
    val quantity: Double,
    val unitPrice: Double,
    /** Remise de la ligne en % (0 à 100). */
    val discountPct: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("productId", productId ?: JSONObject.NULL)
        put("description", description)
        put("unit", unit)
        put("quantity", quantity)
        put("unitPrice", unitPrice)
        put("discountPct", discountPct)
    }
}

fun normalizeDocumentLine(value: Any?): DocumentLine? {
    val l = value as? JSONObject ?: return null
    val description = cleanLabel(l.opt("description"), DocumentLimits.DESCRIPTION_MAX)
    val unit = cleanLabel(l.opt("unit"), DocumentLimits.UNIT_MAX)
    val quantity = finiteNumber(l.opt("quantity"))
    val unitPrice = finiteNumber(l.opt("unitPrice"))

    if (description.isEmpty() || quantity == null || quantity <= 0.0 || quantity > 1_000_000.0 ||
        unitPrice == null || unitPrice < 0.0 || unitPrice > ModuleLimits.AMOUNT_MAX
    ) {
        return null
    }

    val rawPct = finiteNumber(l.opt("discountPct"))
    val pct = if (rawPct != null) minOf(100.0, maxOf(0.0, Math.round(rawPct * 100) / 100.0)) else 0.0
    val productId = (l.opt("productId") as? String)?.takeIf { it.isNotEmpty() && it.length <= 100 }

    return DocumentLine(
        productId = productId,
        description = description,
        unit = unit,
        quantity = Math.round(quantity * 100) / 100.0,
        unitPrice = Math.round(unitPrice).toDouble(),
        discountPct = pct
    )
}

fun documentLineTotal(line: DocumentLine): Double =
    Math.round(line.quantity * line.unitPrice * (1 - line.discountPct / 100)).toDouble()

data class DocumentTotals(
    val subtotal: Double,
    val discount: Double,
    val net: Double,
    val tax: Double,
    val total: Double,
    val deposit: Double,
    val balance: Double
)

/** Remise globale en FCFA, TVA en %, acompte demandé en %. */
fun computeDocumentTotals(
    lines: List<DocumentLine>,
    discount: Double,
    taxRate: Double,
    depositPct: Double
): DocumentTotals {
    val subtotal = lines.sumOf { documentLineTotal(it) }
    val disc = minOf(maxOf(0.0, Math.round(discount).toDouble()), subtotal)
    val net = subtotal - disc
    val tax = Math.round(net * taxRate / 100).toDouble()
    val total = net + tax
    val deposit = Math.round(total * depositPct / 100).toDouble()
    return DocumentTotals(subtotal, disc, net, tax, total, deposit, total - deposit)
}
