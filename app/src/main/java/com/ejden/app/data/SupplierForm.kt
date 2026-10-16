package com.ejden.app.data

enum class SupplierField { Name, Phone }

sealed class SupplierFormResult {
    data class Valid(val name: String, val phone: String) : SupplierFormResult()
    data class Invalid(val message: String, val field: SupplierField) : SupplierFormResult()
}

/** Regles du formulaire fournisseur (ajout et modification). */
fun validateSupplierForm(
    nameRaw: String,
    phoneRaw: String,
    suppliers: List<Supplier>,
    editingId: String?
): SupplierFormResult {
    val name = nameRaw.replace(JS_WHITESPACE_RUN, " ").jsTrim()
    val phone = phoneRaw.jsTrim()

    if (name.isEmpty()) {
        return SupplierFormResult.Invalid("Veuillez saisir le nom du fournisseur.", SupplierField.Name)
    }
    if (name.length > ModuleLimits.TEXT_MAX) {
        return SupplierFormResult.Invalid("Nom trop long (${ModuleLimits.TEXT_MAX} caractères max).", SupplierField.Name)
    }
    if (!isValidPhone(phone)) {
        return SupplierFormResult.Invalid(
            "Téléphone invalide : chiffres, +, espaces et tirets seulement.", SupplierField.Phone
        )
    }
    val wanted = name.lowercase()
    if (suppliers.any { it.id != editingId && it.name.lowercase() == wanted }) {
        return SupplierFormResult.Invalid("Un fournisseur porte déjà ce nom.", SupplierField.Name)
    }
    return SupplierFormResult.Valid(name, phone)
}
