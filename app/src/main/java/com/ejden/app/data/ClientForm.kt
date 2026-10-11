package com.ejden.app.data

enum class ClientField { Name, Phone }

sealed class ClientFormResult {
    data class Valid(val name: String, val phone: String) : ClientFormResult()
    data class Invalid(val message: String, val field: ClientField) : ClientFormResult()
}

/** Règles du formulaire client (ajout et modification). */
fun validateClientForm(
    nameRaw: String,
    phoneRaw: String,
    clients: List<Client>,
    editingId: String?
): ClientFormResult {
    val name = nameRaw.replace(JS_WHITESPACE_RUN, " ").jsTrim()
    val phone = phoneRaw.jsTrim()

    if (name.isEmpty()) {
        return ClientFormResult.Invalid("Veuillez saisir le nom du client.", ClientField.Name)
    }
    if (name.length > ModuleLimits.TEXT_MAX) {
        return ClientFormResult.Invalid("Nom trop long (${ModuleLimits.TEXT_MAX} caractères max).", ClientField.Name)
    }
    if (!isValidPhone(phone)) {
        return ClientFormResult.Invalid(
            "Téléphone invalide : chiffres, +, espaces et tirets seulement.", ClientField.Phone
        )
    }
    val wanted = name.lowercase()
    if (clients.any { it.id != editingId && it.name.lowercase() == wanted }) {
        return ClientFormResult.Invalid("Un client porte déjà ce nom.", ClientField.Name)
    }
    return ClientFormResult.Valid(name, phone)
}
