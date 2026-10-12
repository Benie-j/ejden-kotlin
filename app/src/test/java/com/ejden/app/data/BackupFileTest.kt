package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private class FlakyStore(private val inner: KeyValueStore, private val failKey: String) : KeyValueStore {
    override fun get(key: String) = inner.get(key)
    override fun set(key: String, value: String) = if (key == failKey) false else inner.set(key, value)
    override fun remove(key: String) = inner.remove(key)
}

private fun failed(result: BackupParse) = (result as BackupParse.Failed).error

private fun fileText(
    app: Any? = "EJDEN",
    version: Any? = 1,
    createdAt: Any? = "2026-10-09T14:05:00.000Z",
    data: Any? = JSONObject().put("ejden_products", "[]")
): String {
    val o = JSONObject()
    if (app != null) o.put("app", app)
    if (version != null) o.put("version", version)
    if (createdAt != null) o.put("createdAt", createdAt)
    if (data != null) o.put("data", data)
    return o.toString()
}

class BackupFileTest {

    @Test
    fun creationDUneSauvegarde() {
        val store = InMemoryStore()
        store.set("ejden_products", "[1,2]")
        store.set("ejden_theme", "light")
        store.set("cle_etrangere", "x")

        val backup = createBackup(store, Instant.parse("2026-10-09T14:05:00Z"))
        assertEquals("2026-10-09T14:05:00.000Z", backup.createdAt)
        assertEquals(setOf("ejden_products", "ejden_theme"), backup.data.keys)

        val json = JSONObject(backup.toJsonText())
        assertEquals("EJDEN", json.getString("app"))
        assertEquals(1, json.getInt("version"))
        assertEquals("[1,2]", json.getJSONObject("data").getString("ejden_products"))

        assertEquals("ejden-sauvegarde-2026-10-09.json", backupFileName(LocalDate.of(2026, 10, 9)))
    }

    @Test
    fun fichiersRefuses() {
        assertEquals("Ce fichier n'est pas une sauvegarde valide.", failed(parseBackup("pas du json")))
        assertEquals("Ce fichier n'est pas une sauvegarde valide.", failed(parseBackup("[1] x")))
        assertEquals("Ce fichier n'est pas une sauvegarde valide.", failed(parseBackup("[1,2]")))
        assertEquals("Ce fichier n'est pas une sauvegarde EJDEN.", failed(parseBackup(fileText(app = "AUTRE"))))
        assertEquals("Ce fichier n'est pas une sauvegarde EJDEN.", failed(parseBackup(fileText(version = 2))))
        assertEquals("Sauvegarde incomplète ou corrompue.", failed(parseBackup(fileText(createdAt = null))))
        assertEquals("Sauvegarde incomplète ou corrompue.", failed(parseBackup(fileText(createdAt = "hier"))))
        assertEquals("Sauvegarde incomplète ou corrompue.", failed(parseBackup(fileText(data = null))))
        assertEquals(
            "Sauvegarde corrompue.",
            failed(parseBackup(fileText(data = JSONObject().put("ejden_products", "{pas json"))))
        )
        assertEquals(
            "Sauvegarde corrompue.",
            failed(parseBackup(fileText(data = JSONObject().put("ejden_products", 12))))
        )
        assertEquals(
            "Cette sauvegarde ne contient aucune donnée.",
            failed(parseBackup(fileText(data = JSONObject().put("cle_inconnue", "[]"))))
        )
    }

    @Test
    fun clesInconnuesIgnoreesEtResume() {
        val data = JSONObject()
            .put("ejden_products", """[{"id":"a"},{"id":"b"}]""")
            .put("ejden_sales", """[{"id":"s"}]""")
            .put("ejden_clients", "[]")
            .put("cle_inconnue", "n'importe quoi")
        val result = parseBackup(fileText(data = data)) as BackupParse.Ok

        assertEquals(setOf("ejden_products", "ejden_sales", "ejden_clients"), result.backup.data.keys)
        assertEquals(BackupSummary(2, 1, 0), result.summary)
        assertEquals(
            "Sauvegarde du 09/10/2026 à 14:05 : 2 produits, 1 vente, 0 client.",
            backupSummaryText(result.summary, result.backup.createdAt, ZoneOffset.UTC)
        )
    }

    @Test
    fun restaurationRemplaceLesDonnees() {
        val source = InMemoryStore()
        source.set("ejden_products", """[{"id":"a"}]""")
        source.set("ejden_theme", "light")

        val target = InMemoryStore()
        target.set("ejden_products", "[]")
        target.set("ejden_sales", """[{"id":"vieille"}]""")
        target.set("ejden_clients_backup", "garde")

        val parsed = parseBackup(createBackup(source).toJsonText()) as BackupParse.Ok
        assertTrue(applyBackup(target, parsed.backup))

        assertEquals("""[{"id":"a"}]""", target.get("ejden_products"))
        assertEquals("light", target.get("ejden_theme"))
        assertNull(target.get("ejden_sales"))
        assertEquals("garde", target.get("ejden_clients_backup"))
    }

    @Test
    fun echecDeRestaurationRemetLEtatPrecedent() {
        val inner = InMemoryStore()
        inner.set("ejden_products", """[{"id":"avant"}]""")
        val store = FlakyStore(inner, "ejden_sales")

        val backup = BackupFile(
            "2026-10-09T14:05:00.000Z",
            mapOf("ejden_products" to """[{"id":"apres"}]""", "ejden_sales" to "[]")
        )
        assertEquals(false, applyBackup(store, backup))
        assertEquals("""[{"id":"avant"}]""", inner.get("ejden_products"))
        assertNull(inner.get("ejden_sales"))
    }

    @Test
    fun datesDeSauvegarde() {
        val store = InMemoryStore()
        val repo = BackupRepository(store)

        assertTrue(repo.saveMeta(lastFile = "2026-10-09T14:05:00.000Z"))
        assertTrue(repo.saveMeta(lastDrive = "2026-10-10T08:00:00.000Z"))
        val meta = repo.getMeta()
        assertEquals("2026-10-09T14:05:00.000Z", meta.lastFile)
        assertEquals("2026-10-10T08:00:00.000Z", meta.lastDrive)

        assertEquals("Jamais", formatBackupDate(null))
        assertEquals("09/10/2026 à 14:05", formatBackupDate(meta.lastFile, ZoneOffset.UTC))
    }
}
