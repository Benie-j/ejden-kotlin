package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

private fun form(
    shopName: String = "Boutique",
    shopPhone: String = "",
    firstName: String = "Awa",
    lastName: String = "",
    userPhone: String = "",
    email: String = "",
    tax: String = "",
    validity: String = "",
    dueDays: String = ""
) = SettingsFormInput(shopName, shopPhone, firstName, lastName, userPhone, email, tax, validity, dueDays)

private fun invalid(result: SettingsFormResult) = result as SettingsFormResult.Invalid

class SettingsPanelTest {

    @Test
    fun courrielValide() {
        assertTrue(isValidEmail(""))
        assertTrue(isValidEmail("contact@maboutique.com"))
        assertFalse(isValidEmail("contact@maboutique"))
        assertFalse(isValidEmail("con tact@maboutique.com"))
        assertFalse(isValidEmail("a".repeat(75) + "@b.cd"))
    }

    @Test
    fun reglagesDesDocumentsParDefautEtNettoyes() {
        val defaults = normalizeDocumentSettings(JSONObject())
        assertEquals(30, defaults.defaultValidityDays)
        assertEquals(30, defaults.defaultDueDays)
        assertEquals(0.0, defaults.defaultTaxRate, 0.0)
        assertEquals(DEFAULT_QUOTE_TERMS, defaults.quoteTerms)
        assertEquals(DEFAULT_INVOICE_TERMS, defaults.invoiceTerms)
        assertNull(defaults.logo)

        val raw = JSONObject()
            .put("address", "  Marché\r\nCocody\u0001 ")
            .put("email", "pas un courriel")
            .put("defaultTaxRate", 18.456)
            .put("defaultValidityDays", 1000)
            .put("defaultDueDays", -4)
            .put("quoteTerms", "")
            .put("logo", "http://x.com/a.png")
        val cleaned = normalizeDocumentSettings(raw)
        assertEquals("Marché\nCocody", cleaned.address)
        assertEquals("", cleaned.email)
        assertEquals(18.46, cleaned.defaultTaxRate, 0.0)
        assertEquals(365, cleaned.defaultValidityDays)
        assertEquals(0, cleaned.defaultDueDays)
        assertEquals("", cleaned.quoteTerms)
        assertNull(cleaned.logo)
    }

    @Test
    fun reglagesDesDocumentsEnregistres() {
        val repo = DocumentRepository(InMemoryStore())
        val saved = normalizeDocumentSettings(JSONObject()).copy(
            address = "Abidjan", defaultTaxRate = 18.0, logo = "data:image/png;base64,AAAA"
        )
        assertTrue(repo.saveSettings(saved))
        val read = repo.getSettings()
        assertEquals("Abidjan", read.address)
        assertEquals(18.0, read.defaultTaxRate, 0.0)
        assertEquals("data:image/png;base64,AAAA", read.logo)
    }

    @Test
    fun formulaireMonEspace() {
        assertEquals(
            "Nom de l'entreprise trop long (60 caractères max).",
            invalid(validateSettingsForm(form(shopName = "x".repeat(61)))).message
        )
        assertEquals(
            "Téléphone de l'entreprise invalide : chiffres, +, espaces et tirets seulement.",
            invalid(validateSettingsForm(form(shopPhone = "abc"))).message
        )
        assertEquals(
            "Prénom trop long (40 caractères max).",
            invalid(validateSettingsForm(form(firstName = "x".repeat(41)))).message
        )
        assertEquals(
            "Votre téléphone est invalide : chiffres, +, espaces et tirets seulement.",
            invalid(validateSettingsForm(form(userPhone = "abc"))).message
        )
        assertEquals(
            "E-mail de l'entreprise invalide.",
            invalid(validateSettingsForm(form(email = "faux"))).message
        )
        assertEquals(
            "TVA par défaut invalide (0 à 100 %).",
            invalid(validateSettingsForm(form(tax = "150"))).message
        )
        assertEquals(
            "Validité d'un devis : entre 1 et 365 jours.",
            invalid(validateSettingsForm(form(validity = "0"))).message
        )
        assertEquals(
            "Délai de paiement : entre 0 et 365 jours.",
            invalid(validateSettingsForm(form(dueDays = "366"))).message
        )
    }

    @Test
    fun formulaireMonEspaceValide() {
        val ok = validateSettingsForm(form(tax = "18,5", validity = "", dueDays = "15", shopPhone = " 07 00 ")) as SettingsFormResult.Valid
        assertEquals(18.5, ok.tax, 0.0)
        assertEquals(30, ok.validity)
        assertEquals(15, ok.dueDays)
        assertEquals("07 00", ok.shopPhone)
    }

    @Test
    fun apercuDuRecu() {
        assertEquals(
            "Boutique Awa\nTél : 07 00\n\nREÇU DE VENTE\nN° A1B2C3\n2 \u00D7 Riz 5 kg \u2014 7\u202F000 FCFA\nTOTAL : 7\u202F000 FCFA\n\nMerci de votre confiance !",
            receiptPreviewText(" Boutique Awa ", "07 00")
        )
        assertTrue(receiptPreviewText("", "").startsWith("REÇU DE VENTE"))
    }

    @Test
    fun sauvegardeEtEffacement() {
        val store = InMemoryStore()
        val repo = BackupRepository(store)
        assertEquals(BackupMeta(null, null, ""), repo.getMeta())
        assertEquals("Jamais", lastBackupLabel(null))

        store.set(BackupRepository.META_KEY, """{"lastFile":"2026-10-09T14:05:00.000Z","lastDrive":"pas une date","clientId":"abc"}""")
        val meta = repo.getMeta()
        assertEquals("2026-10-09T14:05:00.000Z", meta.lastFile)
        assertNull(meta.lastDrive)
        assertEquals("", meta.clientId)
        assertEquals("9 oct. 2026, 14:05", lastBackupLabel(meta.lastFile, ZoneOffset.UTC))

        store.set("ejden_products", "[]")
        store.set("ejden_sales", "[]")
        store.set("ejden_theme", "light")
        store.set("ejden_text_size", "large")
        assertTrue(repo.resetAllData())
        assertNull(store.get("ejden_products"))
        assertNull(store.get("ejden_sales"))
        assertNull(store.get(BackupRepository.META_KEY))
        assertEquals("light", store.get("ejden_theme"))
        assertEquals("large", store.get("ejden_text_size"))
    }

    @Test
    fun preferencesEnregistrees() {
        val store = InMemoryStore()
        val appearance = AppearanceRepository(store)
        assertTrue(appearance.saveTheme(ThemeChoice.Auto))
        assertTrue(appearance.saveTextSize(TextSize.XLarge))
        assertTrue(appearance.saveReduceMotion(true))
        val read = appearance.get()
        assertEquals(ThemeChoice.Auto, read.theme)
        assertEquals(TextSize.XLarge, read.textSize)
        assertTrue(read.reduceMotion)

        appearance.saveTextSize(TextSize.Normal)
        assertEquals("normal", store.get("ejden_text_size"))
        assertEquals(TextSize.Normal, appearance.get().textSize)

        assertTrue(PreferencesRepository(store).saveLanguage("fr"))
        assertEquals("fr", store.get("ejden_lang"))
    }
}
