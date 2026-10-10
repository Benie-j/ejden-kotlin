package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun input(
    name: String = "Savon",
    purchase: String = "500",
    sale: String = "750",
    stock: String = "10",
    threshold: String = "",
    barcode: String = "",
    category: String = "",
    photo: String? = null
) = ProductFormInput(name, purchase, sale, stock, threshold, barcode, category, photo)

private fun invalid(result: ProductFormResult): ProductFormResult.Invalid =
    result as ProductFormResult.Invalid

private fun valid(result: ProductFormResult): Product =
    (result as ProductFormResult.Valid).product

class ProductFormTest {

    private val other = Product("p9", "Riz", 1.0, 2.0, 5, 0, "611000123", null, "BOISSONS")

    @Test
    fun champsObligatoiresDansLOrdreDeLaPage() {
        val r1 = invalid(validateNewProduct(input(name = ""), emptyList()))
        assertEquals("Veuillez renseigner ce champ.", r1.message)
        assertEquals(ProductField.Name, r1.field)

        assertEquals(ProductField.Purchase, invalid(validateNewProduct(input(purchase = ""), emptyList())).field)
        assertEquals(ProductField.Sale, invalid(validateNewProduct(input(sale = ""), emptyList())).field)
        assertEquals(ProductField.Stock, invalid(validateNewProduct(input(stock = ""), emptyList())).field)
    }

    @Test
    fun messagesDeValidationDeLAjout() {
        assertEquals(
            "Veuillez saisir le nom du produit.",
            invalid(validateNewProduct(input(name = "   "), emptyList())).message
        )
        assertEquals(
            "Prix de vente invalide.",
            invalid(validateNewProduct(input(sale = "0"), emptyList())).message
        )
        assertEquals(
            "Prix d'achat invalide.",
            invalid(validateNewProduct(input(purchase = "abc"), emptyList())).message
        )
        assertEquals(
            "Stock initial invalide.",
            invalid(validateNewProduct(input(stock = "1.5"), emptyList())).message
        )
        assertEquals(
            "Seuil d'alerte invalide.",
            invalid(validateNewProduct(input(threshold = "2.5"), emptyList())).message
        )
    }

    @Test
    fun ajoutValideNettoieLesChamps() {
        val product = valid(
            validateNewProduct(
                input(
                    name = "  Savon  ", threshold = "", barcode = "  123 ",
                    category = " boissons ", photo = "data:image/png;base64,AAAA"
                ),
                listOf(other)
            )
        )
        assertEquals("Savon", product.name)
        assertEquals(500.0, product.purchasePrice, 0.0)
        assertEquals(750.0, product.salePrice, 0.0)
        assertEquals(10, product.stock)
        assertEquals(0, product.stockThreshold)
        assertEquals("123", product.barcode)
        assertEquals("BOISSONS", product.category)
        assertEquals("data:image/png;base64,AAAA", product.photo)
        assertTrue(product.id.isNotEmpty())
    }

    @Test
    fun ajoutSansCodeNiCategorie() {
        val product = valid(validateNewProduct(input(), emptyList()))
        assertNull(product.barcode)
        assertNull(product.category)
        assertNull(product.photo)
    }

    @Test
    fun codeBarresDejaUtiliseALAjout() {
        val r = invalid(validateNewProduct(input(barcode = "611000123"), listOf(other)))
        assertEquals("Ce code-barres existe déjà.", r.message)
        assertEquals(ProductField.Barcode, r.field)
    }

    @Test
    fun regleDeModification() {
        val product = Product("p1", "Savon", 500.0, 750.0, 10, 0, "999", null, null)
        val all = listOf(product, other)

        assertEquals(
            "Le nom est trop long (80 caractères max).",
            invalid(validateEditedProduct(input(name = "x".repeat(81)), product, all)).message
        )
        assertEquals(
            "Stock invalide.",
            invalid(validateEditedProduct(input(stock = "1000001"), product, all)).message
        )
        assertEquals(
            "Prix de vente invalide.",
            invalid(validateEditedProduct(input(sale = "100000001"), product, all)).message
        )
        assertEquals(
            "Code-barres trop long.",
            invalid(validateEditedProduct(input(barcode = "1".repeat(65)), product, all)).message
        )
        // Code d'un autre produit (espaces ignorés) : refusé.
        assertEquals(
            "Ce code-barres existe déjà.",
            invalid(validateEditedProduct(input(barcode = "611 000 123"), product, all)).message
        )
        // Son propre code : accepté.
        val same = valid(validateEditedProduct(input(barcode = " 999 "), product, all))
        assertEquals("999", same.barcode)
        assertEquals("p1", same.id)
    }

    @Test
    fun seuilParDefaut() {
        val store = InMemoryStore()
        val prefs = PreferencesRepository(store)
        assertEquals(0, prefs.getDefaultThreshold())

        store.set("ejden_default_threshold", "5")
        assertEquals(5, prefs.getDefaultThreshold())

        for (bad in listOf("0", "1000001", "abc", "2.5", "-3")) {
            store.set("ejden_default_threshold", bad)
            assertEquals(0, prefs.getDefaultThreshold())
        }

        assertTrue(prefs.saveDefaultThreshold(7))
        assertEquals(7, prefs.getDefaultThreshold())
        assertTrue(!prefs.saveDefaultThreshold(1_000_001))
    }
}
