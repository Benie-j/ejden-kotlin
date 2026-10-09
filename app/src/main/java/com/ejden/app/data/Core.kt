package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.UUID

object Limits {
    const val NAME_MAX = 80
    const val BARCODE_MAX = 64
    const val MONEY_MAX = 100_000_000.0
    const val STOCK_MAX = 1_000_000.0
    const val PHOTO_MAX = 450_000
    const val CATEGORY_MAX = 40
}

fun newId(): String = UUID.randomUUID().toString()

/** Espaces reconnus par JavaScript (trim, \s). */
private const val JS_SPACES =
    " \t\n\u000B\u000C\r\u00A0\u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006" +
        "\u2007\u2008\u2009\u200A\u2028\u2029\u202F\u205F\u3000\uFEFF"

val JS_WHITESPACE_RUN = Regex(
    "[\\s\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+"
)

fun String.jsTrim(): String = trim { it in JS_SPACES }

/** Nombre fini (équivalent de Number.isFinite), sinon null. */
fun finiteNumber(value: Any?): Double? {
    val n = (value as? Number)?.toDouble() ?: return null
    return if (n.isFinite()) n else null
}

/**
 * Lecture d'une liste. Si des éléments sont illisibles, une copie brute
 * (clé *_backup) est gardée AVANT qu'une sauvegarde ne les efface.
 */
fun <T> readList(store: KeyValueStore, key: String, parse: (Any?) -> T?): List<T> {
    val stored = store.get(key)
    if (stored == null || stored.isEmpty()) return emptyList()

    return try {
        val parsed = JSONTokener(stored).nextValue()
        if (parsed !is JSONArray) {
            backupRaw(store, key, stored)
            emptyList()
        } else {
            val valid = ArrayList<T>()
            for (i in 0 until parsed.length()) {
                val item = parse(parsed.opt(i))
                if (item != null) valid.add(item)
            }
            if (valid.size != parsed.length()) backupRaw(store, key, stored)
            valid
        }
    } catch (e: Exception) {
        backupRaw(store, key, stored)
        emptyList()
    }
}

fun backupRaw(store: KeyValueStore, key: String, raw: String) {
    val backupKey = key + "_backup"
    if (store.get(backupKey) == null) store.set(backupKey, raw)
}

fun writeList(store: KeyValueStore, key: String, items: List<JSONObject>): Boolean {
    val array = JSONArray()
    for (item in items) array.put(item)
    return store.set(key, array.toString())
}
