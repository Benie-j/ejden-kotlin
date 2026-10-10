package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun item(
    id: String,
    name: String,
    stock: Int = 10,
    threshold: Int = 0,
    barcode: String? = null,
    category: String? = null
) = Product(id, name, 100.0, 150.0, stock, threshold, barcode, null, category)

class CatalogTest {

    @Test
    fun cleDeCategorieIgnoreAccentsEtCasse() {
        assertEquals("boissons", categoryKey("  Boîssons "))
        assertEquals("", categoryKey(null))
    }

    @Test
    fun categoriesSansDoublonEtTrieesEnFrancais() {
        val products = listOf(
            item("1", "a", category = "Épicerie"),
            item("2", "b", category = "alimentation"),
            item("3", "c", category = "BOISSONS"),
            item("4", "d", category = "boissons"),
            item("5", "e", category = null)
        )
        assertEquals(listOf("alimentation", "BOISSONS", "Épicerie"), getCategories(products))
    }

    @Test
    fun categorieSaisieReprendLEcritureExistante() {
        val products = listOf(item("1", "a", category = "BOISSONS"))
        assertEquals("BOISSONS", resolveCategory("  boissons ", products))
        assertEquals("Nouveautés", resolveCategory("Nouveautés", products))
        assertNull(resolveCategory("   ", products))
    }

    @Test
    fun etatDuStock() {
        assertEquals(StockStatus.Out, stockStatusOf(item("1", "a", stock = 0)))
        assertEquals(StockStatus.Low, stockStatusOf(item("1", "a", stock = 3, threshold = 5)))
        assertEquals(StockStatus.Normal, stockStatusOf(item("1", "a", stock = 3, threshold = 0)))
        assertEquals(StockStatus.Normal, stockStatusOf(item("1", "a", stock = 50, threshold = 5)))
    }

    @Test
    fun rechercheEtFiltres() {
        val savon = item("1", "Savon", barcode = "611000123", category = "Hygiène")
        val riz = item("2", "Riz", stock = 0)
        val sucre = item("3", "Sucre", stock = 3, threshold = 5)
        val sel = item("4", "Sel", stock = 50, threshold = 5, category = "Cuisine")
        val all = listOf(savon, riz, sucre, sel)

        assertEquals(4, filterProducts(all, "", ProductFilter.All).size)
        assertEquals(listOf(savon), filterProducts(all, "611 000 123", ProductFilter.All))
        assertEquals(listOf(savon), filterProducts(all, "hygi", ProductFilter.All))
        assertEquals(listOf(sel), filterProducts(all, "  SEL ", ProductFilter.All))

        assertEquals(listOf(riz), filterProducts(all, "", ProductFilter.Out))
        assertEquals(listOf(sucre), filterProducts(all, "", ProductFilter.Low))
        assertEquals(listOf(savon, sucre, sel), filterProducts(all, "", ProductFilter.Available))
        assertEquals(emptyList<Product>(), filterProducts(all, "introuvable", ProductFilter.All))
    }

    @Test
    fun groupesParCategorieSansCategorieEnDernier() {
        val products = listOf(
            item("1", "a", category = "Boissons"),
            item("2", "b", category = "épicerie"),
            item("3", "c", category = null),
            item("4", "d", category = "Alimentation"),
            item("5", "e", category = "boissons")
        )
        val groups = groupByCategory(products)
        assertEquals(
            listOf("Alimentation", "Boissons", "épicerie", UNCATEGORIZED_LABEL),
            groups.map { it.name }
        )
        assertEquals(listOf("1", "5"), groups[1].products.map { it.id })
    }

    @Test
    fun prixFormatesCommeEnFrancais() {
        assertEquals("1\u202F500 FCFA", formatPrice(1500.0))
        assertEquals("1\u202F500,5 FCFA", formatPrice(1500.5))
        assertEquals("0 FCFA", formatPrice(0.0))
        assertEquals("100\u202F000\u202F000 FCFA", formatPrice(100000000.0))
        assertEquals("12,346 FCFA", formatPrice(12.3456))
    }

    @Test
    fun preferencesDAffichage() {
        val repo = ProductPrefsRepository(InMemoryStore())
        assertEquals(ProductsViewMode.GridView, repo.getViewMode())
        assertEquals(ProductsGrouping.All, repo.getGrouping())

        repo.saveViewMode(ProductsViewMode.ListView)
        repo.saveGrouping(ProductsGrouping.Category)
        assertEquals(ProductsViewMode.ListView, repo.getViewMode())
        assertEquals(ProductsGrouping.Category, repo.getGrouping())
    }
}
