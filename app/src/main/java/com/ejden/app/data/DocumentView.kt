package com.ejden.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Tout ce qu'il faut pour dessiner un document. Devis, factures et reçus fournissent le même objet. */
data class DocumentParty(
    /** Ex. « Destinataire », « Client ». */
    val label: String,
    val name: String,
    val phone: String,
    val email: String,
    val address: String
)

data class DocumentPayment(val date: String, val label: String, val amount: Double)

data class DocumentBlock(val title: String, val text: String)

data class PrintableDocument(
    /** Titre en capitales : « DEVIS », « FACTURE », « REÇU ». */
    val title: String,
    val number: String,
    val issueDate: String,
    /** Ex. « Valable jusqu'au » ou « Échéance ». */
    val dueLabel: String?,
    val dueDate: String?,
    /** Tampon facultatif (ex. « ACCEPTÉ »). */
    val stamp: String?,
    val party: DocumentParty,
    val subject: String,
    val lines: List<DocumentLine>,
    val taxRate: Double,
    val depositPct: Double,
    val depositLabel: String,
    val totals: DocumentTotals,
    val blocks: List<DocumentBlock>,
    /** Paiements déjà reçus (factures). Vide pour un devis. */
    val payments: List<DocumentPayment> = emptyList(),
    /** Solde restant dû après paiements (factures) ; null = non affiché. */
    val balanceDue: Double? = null,
    /** Mention « Arrêté … à la somme de » placée sous les totaux. */
    val amountInWordsLead: String,
    /** Cadres de signature : client, entreprise. null = aucun. */
    val signatures: Pair<String, String>? = null
)

/* ---------- Montant en lettres ---------- */

private val UNITS = listOf(
    "zéro", "un", "deux", "trois", "quatre", "cinq", "six", "sept", "huit", "neuf",
    "dix", "onze", "douze", "treize", "quatorze", "quinze", "seize"
)
private val TENS = listOf("", "dix", "vingt", "trente", "quarante", "cinquante", "soixante")

private fun belowHundred(n: Int, final: Boolean): String {
    if (n < 17) return UNITS[n]
    if (n < 20) return "dix-" + UNITS[n - 10]

    if (n < 70) {
        val tens = n / 10
        val unit = n % 10
        if (unit == 0) return TENS[tens]
        if (unit == 1) return TENS[tens] + " et un"
        return TENS[tens] + "-" + UNITS[unit]
    }

    if (n < 80) {
        return if (n == 71) "soixante et onze" else "soixante-" + belowHundred(n - 60, false)
    }

    if (n == 80) return if (final) "quatre-vingts" else "quatre-vingt"

    return "quatre-vingt-" + belowHundred(n - 80, false)
}

private fun belowThousand(n: Int, final: Boolean): String {
    val hundreds = n / 100
    val rest = n % 100
    var text = ""

    if (hundreds > 0) {
        text = if (hundreds == 1) "cent" else UNITS[hundreds] + " cent"
        if (rest == 0 && final && hundreds > 1) text += "s"
    }

    if (rest > 0) text += (if (text.isNotEmpty()) " " else "") + belowHundred(rest, final)

    return text
}

/** 1 250 000 → « un million deux cent cinquante mille » ; hors limites → « ». */
fun numberToFrench(value: Double): String {
    if (!value.isFinite()) return ""
    val n = Math.round(value)
    if (n < 0L || n > 999_999_999_999L) return ""
    if (n == 0L) return "zéro"

    val billions = (n / 1_000_000_000L).toInt()
    val millions = ((n / 1_000_000L) % 1000L).toInt()
    val thousands = ((n / 1000L) % 1000L).toInt()
    val rest = (n % 1000L).toInt()
    val parts = ArrayList<String>()

    if (billions > 0) parts.add(belowThousand(billions, true) + " milliard" + (if (billions > 1) "s" else ""))
    if (millions > 0) parts.add(belowThousand(millions, true) + " million" + (if (millions > 1) "s" else ""))
    if (thousands > 0) parts.add(if (thousands == 1) "mille" else belowThousand(thousands, false) + " mille")
    if (rest > 0) parts.add(belowThousand(rest, true))

    return parts.joinToString(" ")
}

/** 2 360 → « Deux mille trois cent soixante francs CFA ». */
fun amountInWords(amount: Double): String {
    val words = numberToFrench(amount)
    if (words.isEmpty()) return ""

    val plural = if (Math.round(amount) > 1L) "francs CFA" else "franc CFA"
    return words.substring(0, 1).uppercase() + words.substring(1) + " " + plural
}

/* ---------- Formats ---------- */

/** Équivalent de toLocaleString("fr-FR", { maximumFractionDigits: 2 }). */
private fun frenchDecimal(value: Double): String {
    val bd = BigDecimal.valueOf(Math.abs(value)).setScale(2, RoundingMode.HALF_UP)
    val parts = bd.toPlainString().split(".")
    val digits = parts[0]
    val fraction = if (parts.size > 1) parts[1].trimEnd('0') else ""

    val grouped = StringBuilder()
    for ((index, ch) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) grouped.append('\u202F')
        grouped.append(ch)
    }

    val negative = value < 0 && (digits != "0" || fraction.isNotEmpty())
    return (if (negative) "-" else "") + grouped.toString() + (if (fraction.isNotEmpty()) "," + fraction else "")
}

fun formatQuantity(value: Double): String = frenchDecimal(value)

fun formatPercent(value: Double): String = frenchDecimal(value) + " %"

/** « 2026-10-05 » → « 05 octobre 2026 ». */
fun formatDay(day: String): String =
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.FRENCH))

private fun fcfa(value: Double): String = formatNumber(value) + " FCFA"

/* ---------- Texte à partager (WhatsApp, SMS, e-mail) ---------- */

fun documentToText(doc: PrintableDocument, shop: ShopSettings): String {
    val out = ArrayList<String>()
    val t = doc.totals

    if (shop.name.isNotEmpty()) out.add("*" + shop.name + "*")
    if (shop.phone.isNotEmpty()) out.add("Tél : " + shop.phone)
    if (shop.name.isNotEmpty() || shop.phone.isNotEmpty()) out.add("")

    out.add("*" + doc.title + " N° " + doc.number + "*")
    out.add("Date : " + formatDay(doc.issueDate))

    if (doc.dueLabel != null && doc.dueDate != null) out.add(doc.dueLabel + " : " + formatDay(doc.dueDate))

    out.add("")
    out.add(doc.party.label + " : " + doc.party.name)

    if (doc.subject.isNotEmpty()) out.add("Objet : " + doc.subject)

    out.add("")

    doc.lines.forEachIndexed { index, line ->
        val qty = if (line.unit.isNotEmpty()) plainNumber(line.quantity) + " " + line.unit else plainNumber(line.quantity)
        out.add((index + 1).toString() + ". " + line.description)
        out.add("    " + qty + " × " + fcfa(line.unitPrice) + " = " + fcfa(documentLineTotal(line)))
    }

    out.add("")

    if (t.discount > 0) out.add("Remise : − " + fcfa(t.discount))
    if (doc.taxRate > 0) {
        out.add("Total HT : " + fcfa(t.net))
        out.add("TVA (" + plainNumber(doc.taxRate) + " %) : " + fcfa(t.tax))
    }

    out.add("*TOTAL" + (if (doc.taxRate > 0) " TTC" else "") + " : " + fcfa(t.total) + "*")

    if (doc.depositPct > 0) {
        out.add(doc.depositLabel + " (" + plainNumber(doc.depositPct) + " %) : " + fcfa(t.deposit))
        out.add("Reste à payer : " + fcfa(t.balance))
    }

    for (payment in doc.payments) {
        out.add("Payé le " + formatDay(payment.date) + " (" + payment.label + ") : " + fcfa(payment.amount))
    }

    val balance = doc.balanceDue
    if (balance != null) {
        out.add(if (balance == 0.0) "*Solde : 0 FCFA — réglée*" else "*Reste à payer : " + fcfa(balance) + "*")
    }

    for (block in doc.blocks) {
        if (block.text.trim().isNotEmpty()) {
            out.add("")
            out.add("_" + block.title + "_")
            out.add(block.text.trim())
        }
    }

    out.add("")
    out.add("Merci de votre confiance.")

    return out.joinToString("\n")
}

/* ---------- Version imprimable (HTML, pour l'impression et le PDF) ---------- */

private fun esc(text: String): String {
    val out = StringBuilder()
    for (ch in text) {
        when (ch) {
            '&' -> out.append("&amp;")
            '<' -> out.append("&lt;")
            '>' -> out.append("&gt;")
            '"' -> out.append("&quot;")
            '\'' -> out.append("&#39;")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

private fun paragraphs(className: String, text: String): String =
    text.split("\n")
        .filter { it.trim().isNotEmpty() }
        .joinToString("") { "<p class=\"" + className + "\">" + esc(it) + "</p>" }

private fun totalRow(label: String, value: String, className: String = ""): String =
    "<div class=\"doc-total-row" + (if (className.isNotEmpty()) " $className" else "") + "\"><span>" +
        esc(label) + "</span><strong>" + esc(value) + "</strong></div>"

private const val DOCUMENT_CSS = """
@page { size: A4; margin: 12mm; }
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: #fff; }
body { font-family: Arial, Helvetica, sans-serif; color: #111; font-size: 11pt; line-height: 1.45; }
.doc-sheet { width: 100%; overflow-wrap: anywhere; }
.doc-sheet p { margin: 0; }
.doc-head { display: flex; justify-content: space-between; gap: 16px; padding-bottom: 14px; border-bottom: 2px solid #176B87; }
.doc-company { flex: 1 1 200px; min-width: 0; }
.doc-meta { flex: 0 1 220px; text-align: right; }
.doc-logo { display: block; max-width: 140px; max-height: 64px; margin-bottom: 8px; object-fit: contain; }
.doc-company-name { margin: 0 0 4px; font-size: 17px; font-weight: 800; color: #176B87; }
.doc-line { color: #333; }
.doc-title { margin: 0; font-size: 24pt; font-weight: 800; letter-spacing: 0.08em; color: #176B87; }
.doc-number { margin: 2px 0 6px !important; font-size: 14px; font-weight: 700; }
.doc-stamp { display: inline-block; margin-top: 8px !important; padding: 3px 10px; border: 2px solid #168A5B; border-radius: 4px; color: #168A5B; font-weight: 800; letter-spacing: 0.08em; transform: rotate(-4deg); }
.doc-party { margin: 16px 0 12px auto; width: 300px; max-width: 100%; padding: 10px 12px; border: 1px solid #c9d3d7; border-radius: 4px; }
.doc-party-label { display: block; margin-bottom: 2px; font-size: 10px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: #68777D; }
.doc-party-name { display: block; font-size: 14px; }
.doc-subject { margin: 0 0 10px !important; font-size: 13px; }
.doc-table { width: 100%; border-collapse: collapse; margin-bottom: 12px; }
.doc-table th { padding: 7px 6px; background: #176B87; color: #fff; font-size: 11px; text-align: left; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.doc-table td { padding: 7px 6px; border-bottom: 1px solid #dde4e7; vertical-align: top; }
.doc-table .is-num { text-align: right; white-space: nowrap; }
.doc-table th.is-num { text-align: right; }
.doc-table tr { break-inside: avoid; }
.doc-totals { width: 320px; max-width: 100%; margin: 0 0 8px auto; break-inside: avoid; }
.doc-total-row { display: flex; justify-content: space-between; gap: 12px; padding: 5px 0; border-bottom: 1px solid #e6ecee; }
.doc-total-row strong { white-space: nowrap; }
.doc-total-row.is-grand { margin-top: 2px; padding: 8px 8px; border: 0; background: #176B87; color: #fff; font-size: 14px; -webkit-print-color-adjust: exact; print-color-adjust: exact; }
.doc-total-row.is-balance { font-size: 13px; color: #176B87; border-bottom: 2px solid #176B87; }
.doc-small { margin: 0 0 6px !important; color: #68777D; font-size: 11px; text-align: right; }
.doc-words { margin: 10px 0 14px !important; font-style: italic; }
.doc-blocks { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; margin: 14px 0 4px; }
.doc-block { min-width: 0; padding: 9px 11px; border: 1px solid #dde4e7; border-left: 3px solid #176B87; border-radius: 4px; background: #f7fafb; break-inside: avoid; }
.doc-block .doc-line + .doc-line { margin-top: 2px; }
.doc-block h3 { margin: 0 0 3px; font-size: 11px; letter-spacing: 0.06em; text-transform: uppercase; color: #176B87; }
.doc-signatures { display: flex; gap: 16px; margin-top: 18px; break-inside: avoid; }
.doc-signature { flex: 1; padding: 8px 10px; border: 1px solid #c9d3d7; border-radius: 4px; font-weight: 600; }
.doc-signature-space { height: 70px; }
.doc-footer { margin-top: 18px; padding-top: 8px; border-top: 1px solid #c9d3d7; color: #68777D; font-size: 10.5px; line-height: 1.5; text-align: center; }
"""

/** Page HTML complète du document (feuille A4), prête pour l'impression ou l'export PDF. */
fun documentToHtml(doc: PrintableDocument, shop: ShopSettings, settings: DocumentSettings): String {
    val out = StringBuilder()
    out.append("<!DOCTYPE html><html lang=\"fr\"><head><meta charset=\"utf-8\">")
    out.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
    out.append("<title>").append(esc(doc.title + " " + doc.number)).append("</title>")
    out.append("<style>").append(DOCUMENT_CSS).append("</style></head><body><article class=\"doc-sheet\">")

    // En-tête : entreprise à gauche, titre à droite.
    out.append("<header class=\"doc-head\"><div class=\"doc-company\">")
    val logo = settings.logo
    if (logo != null) out.append("<img class=\"doc-logo\" alt=\"\" src=\"").append(esc(logo)).append("\">")
    out.append("<h2 class=\"doc-company-name\">")
        .append(esc(if (shop.name.isNotEmpty()) shop.name else "Nom de l'entreprise")).append("</h2>")
    if (settings.address.isNotEmpty()) out.append(paragraphs("doc-line", settings.address))
    if (shop.phone.isNotEmpty()) out.append(paragraphs("doc-line", "Tél : " + shop.phone))
    if (settings.email.isNotEmpty()) out.append(paragraphs("doc-line", settings.email))
    if (settings.website.isNotEmpty()) out.append(paragraphs("doc-line", settings.website))
    if (settings.taxId.isNotEmpty()) out.append(paragraphs("doc-line", "NIF / IFU : " + settings.taxId))
    if (settings.registry.isNotEmpty()) out.append(paragraphs("doc-line", "RCCM : " + settings.registry))
    out.append("</div><div class=\"doc-meta\">")
    out.append("<h1 class=\"doc-title\">").append(esc(doc.title)).append("</h1>")
    out.append("<p class=\"doc-number\">N° ").append(esc(doc.number)).append("</p>")
    out.append("<p class=\"doc-line\">Date : ").append(esc(formatDay(doc.issueDate))).append("</p>")
    if (doc.dueLabel != null && doc.dueDate != null) {
        out.append("<p class=\"doc-line\">").append(esc(doc.dueLabel + " : " + formatDay(doc.dueDate))).append("</p>")
    }
    if (doc.stamp != null) out.append("<p class=\"doc-stamp\">").append(esc(doc.stamp)).append("</p>")
    out.append("</div></header>")

    // Destinataire.
    out.append("<section class=\"doc-party\"><span class=\"doc-party-label\">").append(esc(doc.party.label)).append("</span>")
    out.append("<strong class=\"doc-party-name\">").append(esc(doc.party.name)).append("</strong>")
    if (doc.party.address.isNotEmpty()) out.append(paragraphs("doc-line", doc.party.address))
    if (doc.party.phone.isNotEmpty()) out.append(paragraphs("doc-line", "Tél : " + doc.party.phone))
    if (doc.party.email.isNotEmpty()) out.append(paragraphs("doc-line", doc.party.email))
    out.append("</section>")

    if (doc.subject.isNotEmpty()) {
        out.append("<p class=\"doc-subject\"><strong>Objet : </strong>").append(esc(doc.subject)).append("</p>")
    }

    // Tableau des lignes.
    val showDiscount = doc.lines.any { it.discountPct > 0 }
    out.append("<table class=\"doc-table\"><thead><tr>")
    out.append("<th>N°</th><th>Désignation</th><th class=\"is-num\">Qté</th><th class=\"is-num\">Prix unitaire</th>")
    if (showDiscount) out.append("<th class=\"is-num\">Remise</th>")
    out.append("<th class=\"is-num\">Montant</th></tr></thead><tbody>")

    doc.lines.forEachIndexed { index, line ->
        val quantity = if (line.unit.isNotEmpty()) formatQuantity(line.quantity) + " " + line.unit else formatQuantity(line.quantity)
        out.append("<tr><td>").append(index + 1).append("</td>")
        out.append("<td class=\"doc-desc\">").append(esc(line.description)).append("</td>")
        out.append("<td class=\"is-num\">").append(esc(quantity)).append("</td>")
        out.append("<td class=\"is-num\">").append(esc(formatNumber(line.unitPrice))).append("</td>")
        if (showDiscount) {
            out.append("<td class=\"is-num\">")
                .append(esc(if (line.discountPct > 0) formatPercent(line.discountPct) else "—")).append("</td>")
        }
        out.append("<td class=\"is-num\">").append(esc(formatNumber(documentLineTotal(line)))).append("</td></tr>")
    }
    out.append("</tbody></table>")

    // Totaux.
    val t = doc.totals
    val hasTax = doc.taxRate > 0
    out.append("<section class=\"doc-totals\">")
    out.append(
        totalRow(
            if (hasTax || t.discount > 0) "Sous-total" else "Total",
            fcfa(t.subtotal),
            if (hasTax || t.discount > 0) "" else "is-grand"
        )
    )
    if (t.discount > 0) out.append(totalRow("Remise", "− " + fcfa(t.discount)))
    if (hasTax) {
        out.append(totalRow("Total HT", fcfa(t.net)))
        out.append(totalRow("TVA (" + formatPercent(doc.taxRate) + ")", fcfa(t.tax)))
        out.append(totalRow("Total TTC", fcfa(t.total), "is-grand"))
    } else if (t.discount > 0) {
        out.append(totalRow("Total net à payer", fcfa(t.total), "is-grand"))
    }
    if (doc.depositPct > 0) {
        out.append(totalRow(doc.depositLabel + " (" + formatPercent(doc.depositPct) + ")", fcfa(t.deposit)))
        out.append(totalRow("Reste à payer", fcfa(t.balance)))
    }
    for (payment in doc.payments) {
        out.append(totalRow("Payé le " + formatDay(payment.date) + " (" + payment.label + ")", "− " + fcfa(payment.amount)))
    }
    val balance = doc.balanceDue
    if (balance != null) {
        out.append(totalRow(if (balance == 0.0) "Solde" else "Reste à payer", fcfa(balance), "is-balance"))
    }
    out.append("</section>")

    if (!hasTax) out.append("<p class=\"doc-small\">TVA non applicable.</p>")

    val words = amountInWords(t.total)
    if (words.isNotEmpty()) {
        out.append("<p class=\"doc-words\">").append(esc(doc.amountInWordsLead + " "))
            .append("<strong>").append(esc(words)).append("</strong>.</p>")
    }

    // Conditions, modalités, notes.
    val blocks = doc.blocks.filter { it.text.trim().isNotEmpty() }
    if (blocks.isNotEmpty()) {
        out.append("<div class=\"doc-blocks\">")
        for (block in blocks) {
            out.append("<section class=\"doc-block\"><h3>").append(esc(block.title)).append("</h3>")
            out.append(paragraphs("doc-line", block.text)).append("</section>")
        }
        out.append("</div>")
    }

    // Signatures.
    val signatures = doc.signatures
    if (signatures != null) {
        out.append("<section class=\"doc-signatures\">")
        for (label in listOf(signatures.first, signatures.second)) {
            out.append("<div class=\"doc-signature\"><span>").append(esc(label))
                .append("</span><div class=\"doc-signature-space\"></div></div>")
        }
        out.append("</section>")
    }

    if (settings.footer.trim().isNotEmpty()) {
        out.append("<footer class=\"doc-footer\">").append(paragraphs("doc-footer-line", settings.footer)).append("</footer>")
    }

    out.append("</article></body></html>")
    return out.toString()
}
