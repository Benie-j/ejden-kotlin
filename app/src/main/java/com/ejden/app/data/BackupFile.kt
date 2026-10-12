package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

const val BACKUP_VALUE_MAX = 5_000_000
const val BACKUP_FILE_MAX = 25_000_000

private val KEY_SET = BACKUP_KEYS.toSet()

/** Une sauvegarde : { app: "EJDEN", version: 1, createdAt, data: { clé: texte JSON } }. */
data class BackupFile(val createdAt: String, val data: Map<String, String>) {
    fun toJsonText(): String {
        val values = JSONObject()
        for ((key, value) in data) values.put(key, value)
        return JSONObject()
            .put("app", "EJDEN")
            .put("version", 1)
            .put("createdAt", createdAt)
            .put("data", values)
            .toString()
    }
}

data class BackupSummary(val products: Int, val sales: Int, val clients: Int)

sealed class BackupParse {
    data class Ok(val backup: BackupFile, val summary: BackupSummary) : BackupParse()
    data class Failed(val error: String) : BackupParse()
}

fun createBackup(store: KeyValueStore, now: Instant = Instant.now()): BackupFile {
    val data = LinkedHashMap<String, String>()
    for (key in BACKUP_KEYS) {
        val value = store.get(key)
        if (value != null) data[key] = value
    }
    return BackupFile(toIso(now), data)
}

fun backupFileName(date: LocalDate = LocalDate.now()): String = "ejden-sauvegarde-$date.json"

/** Lecture stricte (comme JSON.parse) : tout le texte doit être un JSON valide. */
private fun parseStrict(text: String): Any? {
    val tokener = JSONTokener(text)
    val value = tokener.nextValue()
    if (tokener.nextClean() != '\u0000') throw JSONException("Texte en trop")
    return value
}

private fun isValidJson(text: String): Boolean = try {
    parseStrict(text)
    true
} catch (e: Exception) {
    false
}

private fun countItems(data: Map<String, String>, key: String): Int {
    val text = data[key] ?: return 0
    return try {
        (parseStrict(text) as? JSONArray)?.length() ?: 0
    } catch (e: Exception) {
        0
    }
}

/** Vérifie un fichier de sauvegarde : un fichier étranger ne peut rien injecter. */
fun parseBackup(text: String): BackupParse {
    if (text.length > BACKUP_FILE_MAX) return BackupParse.Failed("Fichier trop volumineux.")

    val parsed = try {
        parseStrict(text)
    } catch (e: Exception) {
        return BackupParse.Failed("Ce fichier n'est pas une sauvegarde valide.")
    }
    val file = parsed as? JSONObject
        ?: return BackupParse.Failed("Ce fichier n'est pas une sauvegarde valide.")

    val version = (file.opt("version") as? Number)?.toDouble()
    if (file.opt("app") != "EJDEN" || version != 1.0) {
        return BackupParse.Failed("Ce fichier n'est pas une sauvegarde EJDEN.")
    }

    val createdAt = (file.opt("createdAt") as? String)?.takeIf { parseDateMillis(it) != null }
    val rawData = file.opt("data") as? JSONObject
    if (createdAt == null || rawData == null) {
        return BackupParse.Failed("Sauvegarde incomplète ou corrompue.")
    }

    val data = LinkedHashMap<String, String>()
    for (key in rawData.keys()) {
        if (key !in KEY_SET) continue
        val value = rawData.opt(key)
        if (value !is String || value.length > BACKUP_VALUE_MAX || !isValidJson(value)) {
            return BackupParse.Failed("Sauvegarde corrompue.")
        }
        data[key] = value
    }
    if (data.isEmpty()) return BackupParse.Failed("Cette sauvegarde ne contient aucune donnée.")

    return BackupParse.Ok(
        BackupFile(createdAt, data),
        BackupSummary(
            countItems(data, "ejden_products"),
            countItems(data, "ejden_sales"),
            countItems(data, "ejden_clients")
        )
    )
}

/** Remplace les données actuelles. En cas d'échec, l'état précédent est remis. */
fun applyBackup(store: KeyValueStore, backup: BackupFile): Boolean {
    val previous = createBackup(store).data

    fun write(source: Map<String, String>): Boolean {
        for (key in BACKUP_KEYS) {
            val value = source[key]
            if (value == null) {
                store.remove(key)
            } else if (!store.set(key, value)) {
                return false
            }
        }
        return true
    }

    if (write(backup.data)) return true
    write(previous)
    return false
}

/** Retient la date d'une sauvegarde (fichier ou Drive) sans toucher au reste. */
fun BackupRepository.saveMeta(lastFile: String? = null, lastDrive: String? = null): Boolean {
    val current = getMeta()
    val json = JSONObject()
        .put("lastFile", lastFile ?: current.lastFile ?: JSONObject.NULL)
        .put("lastDrive", lastDrive ?: current.lastDrive ?: JSONObject.NULL)
        .put("clientId", current.clientId)
    return store.set(BackupRepository.META_KEY, json.toString())
}

/** « Jamais » ou « 09/10/2026 à 14:05 ». */
fun formatBackupDate(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String {
    if (iso == null) return "Jamais"
    val millis = parseDateMillis(iso) ?: return "Jamais"
    return DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE)
        .format(Instant.ofEpochMilli(millis).atZone(zone))
}

fun backupSummaryText(summary: BackupSummary, createdAt: String, zone: ZoneId = ZoneId.systemDefault()): String {
    fun plural(n: Int, word: String) = "$n $word" + if (n > 1) "s" else ""
    return "Sauvegarde du ${formatBackupDate(createdAt, zone)} : " +
        "${plural(summary.products, "produit")}, ${plural(summary.sales, "vente")}, ${plural(summary.clients, "client")}."
}
