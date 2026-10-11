package com.ejden.app.data

enum class SettingsField { ShopName, ShopPhone, FirstName, LastName, UserPhone, Email, Tax, Validity, DueDays }

/** Texte tel que saisi dans la page Mon espace. */
data class SettingsFormInput(
    val shopName: String,
    val shopPhone: String,
    val firstName: String,
    val lastName: String,
    val userPhone: String,
    val email: String,
    val tax: String,
    val validity: String,
    val dueDays: String
)

sealed class SettingsFormResult {
    data class Valid(
        val shopName: String,
        val shopPhone: String,
        val firstName: String,
        val lastName: String,
        val userPhone: String,
        val email: String,
        val tax: Double,
        val validity: Int,
        val dueDays: Int
    ) : SettingsFormResult()

    data class Invalid(val message: String, val field: SettingsField) : SettingsFormResult()
}

private fun numberOrNan(text: String): Double =
    if (text.isEmpty()) 0.0 else (text.toDoubleOrNull() ?: Double.NaN)

private fun isWholeNumber(value: Double): Boolean = value.isFinite() && value == Math.floor(value)

fun validateSettingsForm(input: SettingsFormInput): SettingsFormResult {
    val shopName = input.shopName.jsTrim()
    val shopPhone = input.shopPhone.jsTrim()
    val firstName = input.firstName.jsTrim()
    val lastName = input.lastName.jsTrim()
    val userPhone = input.userPhone.jsTrim()
    val email = input.email.jsTrim()

    if (shopName.length > ShopLimits.NAME_MAX) {
        return SettingsFormResult.Invalid(
            "Nom de l'entreprise trop long (${ShopLimits.NAME_MAX} caractères max).", SettingsField.ShopName
        )
    }
    if (!isValidPhone(shopPhone)) {
        return SettingsFormResult.Invalid(
            "Téléphone de l'entreprise invalide : chiffres, +, espaces et tirets seulement.", SettingsField.ShopPhone
        )
    }
    if (firstName.length > UserLimits.NAME_MAX) {
        return SettingsFormResult.Invalid(
            "Prénom trop long (${UserLimits.NAME_MAX} caractères max).", SettingsField.FirstName
        )
    }
    if (lastName.length > UserLimits.NAME_MAX) {
        return SettingsFormResult.Invalid(
            "Nom trop long (${UserLimits.NAME_MAX} caractères max).", SettingsField.LastName
        )
    }
    if (!isValidPhone(userPhone)) {
        return SettingsFormResult.Invalid(
            "Votre téléphone est invalide : chiffres, +, espaces et tirets seulement.", SettingsField.UserPhone
        )
    }
    if (!isValidEmail(email)) {
        return SettingsFormResult.Invalid("E-mail de l'entreprise invalide.", SettingsField.Email)
    }

    val taxText = input.tax.jsTrim().replace(",", ".")
    val tax = numberOrNan(taxText)
    if (!tax.isFinite() || tax < 0.0 || tax > 100.0) {
        return SettingsFormResult.Invalid("TVA par défaut invalide (0 à 100 %).", SettingsField.Tax)
    }

    val validityText = input.validity.jsTrim()
    val validity = if (validityText.isEmpty()) 30.0 else (validityText.toDoubleOrNull() ?: Double.NaN)
    if (!isWholeNumber(validity) || validity < 1.0 || validity > 365.0) {
        return SettingsFormResult.Invalid("Validité d'un devis : entre 1 et 365 jours.", SettingsField.Validity)
    }

    val dueText = input.dueDays.jsTrim()
    val due = if (dueText.isEmpty()) 30.0 else (dueText.toDoubleOrNull() ?: Double.NaN)
    if (!isWholeNumber(due) || due < 0.0 || due > 365.0) {
        return SettingsFormResult.Invalid("Délai de paiement : entre 0 et 365 jours.", SettingsField.DueDays)
    }

    return SettingsFormResult.Valid(
        shopName, shopPhone, firstName, lastName, userPhone, email, tax, validity.toInt(), due.toInt()
    )
}

/** Aperçu du reçu : uniquement les informations de l'ENTREPRISE. */
fun receiptPreviewText(shopName: String, shopPhone: String): String {
    val name = shopName.jsTrim()
    val phone = shopPhone.jsTrim()
    val lines = ArrayList<String>()
    if (name.isNotEmpty()) lines.add(name)
    if (phone.isNotEmpty()) lines.add("Tél : $phone")
    if (name.isNotEmpty() || phone.isNotEmpty()) lines.add("")
    lines.add("REÇU DE VENTE")
    lines.add("N° A1B2C3")
    lines.add("2 \u00D7 Riz 5 kg \u2014 7\u202F000 FCFA")
    lines.add("TOTAL : 7\u202F000 FCFA")
    lines.add("")
    lines.add("Merci de votre confiance !")
    return lines.joinToString("\n")
}
