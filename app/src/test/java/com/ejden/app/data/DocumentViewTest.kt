package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val VIEW_SHOP = ShopSettings("Boutique Awa", "07 00 00")

private fun viewSettings(footer: String = "Merci !") = DocumentSettings(
    address = "Cocody, Abidjan\nRue 12",
    email = "contact@awa.ci",
    website = "",
    taxId = "NIF123",
    registry = "",
    paymentInfo = "Mobile Money : 07 00 00",
    logo = null,
    defaultTaxRate = 18.0,
    defaultValidityDays = 30,
    quoteTerms = "",
    defaultDueDays = 30,
    invoiceTerms = "",
    footer = footer
)

private fun viewQuote(clientName: String = "Awa Koné", status: QuoteStatus = QuoteStatus.Draft) = Quote(
    id = "q1",
    number = "DEV-2026-0001",
    status = status,
    createdAt = "2026-10-10T09:00:00.000Z",
    updatedAt = "2026-10-10T09:00:00.000Z",
    issueDate = "2026-10-05",
    validUntil = "2026-11-04",
    clientName = clientName,
    clientPhone = "07 11",
    subject = "Matériel de bureau",
    lines = listOf(
        DocumentLine(null, "Riz 25 kg", "sac", 2.0, 1000.0, 0.0),
        DocumentLine(null, "Huile", "", 1.5, 500.0, 10.0)
    ),
    taxRate = 18.0,
    deliveryTerms = "7 jours",
    notes = "Livraison à Cocody"
)

class DocumentViewTest {

    @Test
    fun nombresEnLettres() {
        val cases = listOf(
            0 to "zéro", 1 to "un", 16 to "seize", 17 to "dix-sept", 21 to "vingt et un",
            22 to "vingt-deux", 70 to "soixante-dix", 71 to "soixante et onze", 72 to "soixante-douze",
            80 to "quatre-vingts", 81 to "quatre-vingt-un", 91 to "quatre-vingt-onze",
            99 to "quatre-vingt-dix-neuf", 100 to "cent", 101 to "cent un", 200 to "deux cents",
            201 to "deux cent un", 1000 to "mille", 1001 to "mille un", 2000 to "deux mille",
            80000 to "quatre-vingt mille", 200000 to "deux cent mille", 1000000 to "un million",
            2000000 to "deux millions", 1250000 to "un million deux cent cinquante mille",
            1000000000 to "un milliard"
        )
        for ((number, words) in cases) {
            assertEquals("nombre $number", words, numberToFrench(number.toDouble()))
        }
        assertEquals("", numberToFrench(-1.0))
        assertEquals("", numberToFrench(1e13))
        assertEquals("", numberToFrench(Double.NaN))
    }

    @Test
    fun montantEnLettres() {
        assertEquals("Zéro franc CFA", amountInWords(0.0))
        assertEquals("Un franc CFA", amountInWords(1.0))
        assertEquals("Deux francs CFA", amountInWords(2.0))
        assertEquals("Deux mille trois cent soixante francs CFA", amountInWords(2360.0))
        assertEquals("Un million deux cent cinquante mille francs CFA", amountInWords(1250000.0))
        assertEquals("", amountInWords(-5.0))
    }

    @Test
    fun formatsFrancais() {
        assertEquals("05 octobre 2026", formatDay("2026-10-05"))
        assertEquals("2", formatQuantity(2.0))
        assertEquals("2,5", formatQuantity(2.5))
        assertEquals("2,35", formatQuantity(2.346))
        assertEquals("1\u202F234,5", formatQuantity(1234.5))
        assertEquals("18 %", formatPercent(18.0))
        assertEquals("5,5 %", formatPercent(5.5))
    }

    @Test
    fun devisVersDocument() {
        val settings = viewSettings()
        val draft = quoteToDocument(viewQuote(), VIEW_SHOP, settings, QuoteDisplayStatus.Draft)
        assertEquals("DEVIS", draft.title)
        assertEquals("Valable jusqu'au", draft.dueLabel)
        assertNull(draft.stamp)
        assertEquals(5, draft.blocks.size)
        assertEquals("Pour Boutique Awa", draft.signatures!!.second)

        assertEquals("ACCEPTÉ", quoteToDocument(viewQuote(), VIEW_SHOP, settings, QuoteDisplayStatus.Accepted).stamp)
        assertEquals("REFUSÉ", quoteToDocument(viewQuote(), VIEW_SHOP, settings, QuoteDisplayStatus.Refused).stamp)
        assertEquals("EXPIRÉ", quoteToDocument(viewQuote(), VIEW_SHOP, settings, QuoteDisplayStatus.Expired).stamp)

        val noShop = quoteToDocument(viewQuote(), ShopSettings("", ""), settings, QuoteDisplayStatus.Draft)
        assertEquals("Pour l'entreprise", noShop.signatures!!.second)
    }

    @Test
    fun texteAPartager() {
        val doc = quoteToDocument(viewQuote(), VIEW_SHOP, viewSettings(), QuoteDisplayStatus.Draft)
        val lines = documentToText(doc, VIEW_SHOP).split("\n")

        assertEquals("*Boutique Awa*", lines[0])
        assertEquals("Tél : 07 00 00", lines[1])
        assertTrue(lines.contains("*DEVIS N° DEV-2026-0001*"))
        assertTrue(lines.contains("Date : 05 octobre 2026"))
        assertTrue(lines.contains("Valable jusqu'au : 04 novembre 2026"))
        assertTrue(lines.contains("Destinataire : Awa Koné"))
        assertTrue(lines.contains("Objet : Matériel de bureau"))
        assertTrue(lines.contains("1. Riz 25 kg"))
        assertTrue(lines.contains("    2 sac × " + formatNumber(1000.0) + " FCFA = " + formatNumber(2000.0) + " FCFA"))
        // Lignes : 2 × 1 000 = 2 000 et 1,5 × 500 − 10 % = 675, soit 2 675 ; TVA 18 % = 481,5 arrondi à 482.
        assertTrue(lines.contains("TVA (18 %) : " + formatNumber(482.0) + " FCFA"))
        assertTrue(lines.contains("*TOTAL TTC : " + formatNumber(3157.0) + " FCFA*"))
        assertTrue(lines.contains("_Délai de livraison_"))
        assertTrue(lines.contains("7 jours"))
        assertEquals("Merci de votre confiance.", lines.last())
    }

    @Test
    fun versionImprimableEchappeLeTexte() {
        val doc = quoteToDocument(
            viewQuote(clientName = "<script>alert(1)</script> & Cie"),
            VIEW_SHOP, viewSettings(), QuoteDisplayStatus.Accepted
        )
        val html = documentToHtml(doc, VIEW_SHOP, viewSettings())

        assertFalse(html.contains("<script"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Cie"))
        assertTrue(html.contains("DEVIS"))
        assertTrue(html.contains("N° DEV-2026-0001"))
        assertTrue(html.contains("ACCEPTÉ"))
        assertTrue(html.contains("Total TTC"))
        assertTrue(html.contains("Arrêté le présent devis à la somme de"))
        assertTrue(html.contains("Trois mille cent cinquante-sept francs CFA"))
        // L'adresse de l'entreprise est coupée en lignes.
        assertTrue(html.contains("<p class=\"doc-line\">Rue 12</p>"))
        // Une remise de ligne ajoute la colonne « Remise ».
        assertTrue(html.contains("<th class=\"is-num\">Remise</th>"))
    }

    @Test
    fun sansTvaNiRemise() {
        val quote = viewQuote().copy(
            taxRate = 0.0,
            lines = listOf(DocumentLine(null, "Riz", "", 1.0, 1000.0, 0.0))
        )
        val doc = quoteToDocument(quote, VIEW_SHOP, viewSettings(footer = ""), QuoteDisplayStatus.Draft)
        val html = documentToHtml(doc, VIEW_SHOP, viewSettings(footer = ""))

        assertTrue(html.contains("TVA non applicable."))
        assertFalse(html.contains("Total TTC"))
        assertFalse(html.contains("<footer"))
        assertFalse(html.contains("<th class=\"is-num\">Remise</th>"))
    }
}
