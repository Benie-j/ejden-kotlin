package com.ejden.app.data

import java.io.File

/** Équivalent du localStorage : clé -> texte JSON. */
interface KeyValueStore {
    fun get(key: String): String?
    fun set(key: String, value: String): Boolean
    fun remove(key: String)
}

class InMemoryStore : KeyValueStore {
    private val map = LinkedHashMap<String, String>()
    override fun get(key: String): String? = map[key]
    override fun set(key: String, value: String): Boolean {
        map[key] = value
        return true
    }
    override fun remove(key: String) {
        map.remove(key)
    }
}

/** Un fichier par clé, écriture atomique (fichier temporaire puis renommage). */
class FileStore(private val dir: File) : KeyValueStore {
    init {
        dir.mkdirs()
    }

    private fun file(key: String): File =
        File(dir, key.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    @Synchronized
    override fun get(key: String): String? = try {
        val f = file(key)
        if (f.isFile) f.readText(Charsets.UTF_8) else null
    } catch (e: Exception) {
        null
    }

    @Synchronized
    override fun set(key: String, value: String): Boolean = try {
        val target = file(key)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(value, Charsets.UTF_8)
        if (tmp.renameTo(target)) {
            true
        } else {
            target.delete()
            tmp.renameTo(target)
        }
    } catch (e: Exception) {
        false
    }

    @Synchronized
    override fun remove(key: String) {
        try {
            file(key).delete()
        } catch (e: Exception) {
            // rien à faire
        }
    }
}
