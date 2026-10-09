package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRepositoryTest {

    private fun obj(vararg p: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in p) o.put(k, v ?: JSONObject.NULL)
        return o
    }

    private fun raw(vararg items: JSONObject): String = JSONArray(items.toList()).toString()

    private fun repoWith(json: String?): Pair<ProductRepository, InMemoryStore> {
        val store = InMemoryStore()
        if (json != null) store.set(ProductRepository.KEY, json)
        return ProductRepository(store) to store
    }

    private fun valid(vararg extra: Pair<String, Any?>): JSONObject {
        val o = obj(
            "id" to "p1", "name" to "Savon",
            "purchasePrice" to 500, "salePrice" to 750, "stock" to 12
        )
        for ((k, v) in extra) o.put(k, v ?: JSONObject.NULL)
        return o
    }

    @Test
    fun ancienProduitSansChampsOptionnelsEstAccepte() {
        val (repo, _) = repoWith(raw(valid("name" to "  Savon  ", "stock" to 12.9)))
        val p = repo.getProducts().single()
        assertEquals("Savon", p.name)
        assertEquals(12, p.stock)
        assertEquals(0, p.stockThreshold)
        assertNull(p.barcode)
        assertNull(p.photo)
        assertNull(p.category)
        assertEquals(750.0, p.salePrice, 0.0)
    }

    @Test
    fun elementsInvalidesIgnoresEtCopieDeSecurite() {
        val json = raw(valid(), obj("id" to "", "name" to "x"))
        val (repo, store) = repoWith(json)
        assertEquals(1, repo.getProducts().size)
        assertEquals(json, store.get("ejden_products_backup"))
    }

    @Test
    fun donneesNonListeSontSauvegardeesPuisIgnorees() {
        val (repo, store) = repoWith("{\"a\":1}")
        assertTrue(repo.getProducts().isEmpty())
        assertEquals("{\"a\":1}", store.get("ejden_products_backup"))
    }

    @Test
    fun valeursBorneesEtNegatifsRamenesAZero() {
        val (repo, _) = repoWith(
            raw(valid("salePrice" to 2_000_000_000L, "stock" to -5, "stockThreshold" to 2_000_000))
        )
        val p = repo.getProducts().single()
        assertEquals(100_000_000.0, p.salePrice, 0.0)
        assertEquals(0, p.stock)
        assertEquals(1_000_000, p.stockThreshold)
    }

    @Test
    fun categorieEtCodeBarresNettoyes() {
        val (repo, _) = repoWith(
            raw(valid("category" to "  Bois\tsons \n  fra\u00eeches ", "barcode" to " 123 456\u00A0789 "))
        )
        val p = repo.getProducts().single()
        assertEquals("Bois sons fra\u00eeches", p.category)
        assertEquals("123456789", p.barcode)
    }

    @Test
    fun photoDangereuseRefuseeEtPhotoSureConservee() {
        val (a, _) = repoWith(raw(valid("photo" to "http://x.com/a.png")))
        assertNull(a.getProducts().single().photo)
        val ok = "data:image/png;base64,AAAA"
        val (b, _) = repoWith(raw(valid("photo" to ok)))
        assertEquals(ok, b.getProducts().single().photo)
    }

    @Test
    fun ajoutModificationSuppression() {
        val (repo, _) = repoWith(null)
        val p = Product(newId(), "Riz", 1000.0, 1500.0, 20, 5, null, null, "Alimentation")

        assertTrue(repo.addProduct(p))
        assertEquals(1, repo.getProducts().size)
        assertNotNull(repo.getProductById(p.id))

        assertFalse(repo.addProduct(p.copy(id = newId(), name = "   ")))
        assertFalse(repo.updateProduct(p.copy(id = "inconnu")))

        assertTrue(repo.updateProduct(p.copy(salePrice = 1600.0)))
        assertEquals(1600.0, repo.getProductById(p.id)!!.salePrice, 0.0)

        assertTrue(repo.deleteProduct(p.id))
        assertFalse(repo.deleteProduct(p.id))
        assertTrue(repo.getProducts().isEmpty())
    }
}
