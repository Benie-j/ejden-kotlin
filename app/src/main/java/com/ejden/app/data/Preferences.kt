package com.ejden.app.data

/** Préférences simples de l'application (texte brut, comme l'ancienne app). */
class PreferencesRepository(private val store: KeyValueStore) {
    companion object {
        const val THRESHOLD_KEY = "ejden_default_threshold"
        const val CREDIT_KEY = "ejden_allow_credit"
    }

    /** Seuil d'alerte proposé pour un nouveau produit (0 = aucun). */
    fun getDefaultThreshold(): Int {
        val raw = store.get(THRESHOLD_KEY)
        val value = if (raw == null) {
            0.0
        } else {
            val text = raw.jsTrim()
            if (text.isEmpty()) 0.0 else (text.toDoubleOrNull() ?: Double.NaN)
        }
        val valid = value.isFinite() && value == Math.floor(value) && value > 0.0 && value <= 1_000_000.0
        return if (valid) value.toInt() else 0
    }

    fun saveDefaultThreshold(value: Int): Boolean {
        if (value < 0 || value > 1_000_000) return false
        return store.set(THRESHOLD_KEY, value.toString())
    }

    /** Ventes à crédit autorisées (oui par défaut). */
    fun getAllowCredit(): Boolean = store.get(CREDIT_KEY) != "0"

    fun saveAllowCredit(enabled: Boolean): Boolean = store.set(CREDIT_KEY, if (enabled) "1" else "0")
}
