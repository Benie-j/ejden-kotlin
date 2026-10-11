package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Toutes les clés de données de l'application (même liste que l'ancienne version). */
val BACKUP_KEYS: List<String> = listOf(
    "ejden_products",
    "ejden_sales",
    "ejden_clients",
    "ejden_credit_payments",
    "ejden_expenses",
    "ejden_cash",
    "ejden_stock_movements",
    "ejden_suppliers",
    "ejden_purchases",
    "ejden_supplier_payments",
    "ejden_orders",
    "ejden_quotes",
    "ejden_invoices",
    "ejden_doc_counters",
    "ejden_doc_settings",
    "ejden_shop",
    "ejden_user",
    "ejden_notif_settings",
    "ejden_notif_state",
    "ejden_products_grouping",
    "ejden_products_view",
    "ejden_dashboard_period",
    "ejden_finances_period",
    "ejden_theme",
    "ejden_lang",
    "ejden_text_size",
    "ejden_reduce_motion",
    "ejden_default_threshold",
    "ejden_allow_credit"
)

private val KEPT_ON_RESET = setOf("ejden_theme", "ejden_lang", "ejden_text_size", "ejden_reduce_motion")
private val CLIENT_ID_PATTERN = Regex("[A-Za-z0-9._-]{10,120}\\.apps\\.googleusercontent\\.com")

data class BackupMeta(val lastFile: String?, val lastDrive: String?, val clientId: String)

class BackupRepository(private val store: KeyValueStore) {
    companion object {
        const val META_KEY = "ejden_backup_meta"
    }

    fun getMeta(): BackupMeta {
        val stored = store.get(META_KEY)
        if (stored != null && stored.isNotEmpty()) {
            try {
                val data = JSONTokener(stored).nextValue() as? JSONObject
                if (data != null) {
                    val id = (data.opt("clientId") as? String)?.jsTrim() ?: ""
                    return BackupMeta(
                        lastFile = validDate(data.opt("lastFile")),
                        lastDrive = validDate(data.opt("lastDrive")),
                        clientId = if (CLIENT_ID_PATTERN.matches(id)) id else ""
                    )
                }
            } catch (e: Exception) {
                // état illisible : valeurs par défaut
            }
        }
        return BackupMeta(null, null, "")
    }

    /** Efface toutes les données (les préférences d'affichage sont conservées). */
    fun resetAllData(): Boolean {
        for (key in BACKUP_KEYS) {
            if (key !in KEPT_ON_RESET) store.remove(key)
        }
        store.remove(META_KEY)
        store.remove("ejden_editing_product")
        return true
    }
}

/** « Jamais » ou « 9 oct. 2026, 14:05 ». */
fun lastBackupLabel(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (iso == null) return "Jamais"
    val millis = parseDateMillis(iso) ?: return "Jamais"
    return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.FRANCE).format(Instant.ofEpochMilli(millis).atZone(zone))
}
