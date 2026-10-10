package com.ejden.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

const val UNCATEGORIZED_LABEL = "Sans catégorie"

private val COMBINING_MARKS = Regex("[\\u0300-\\u036f]")

/** Clé de comparaison : « Boissons », « boissons » et « Boîssons » sont la même catégorie. */
fun categoryKey(category: String?): String =
    Normalizer.normalize(category ?: "", Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase()
        .jsTrim()

private fun frenchCollator(): Collator = Collator.getInstance(Locale.FRENCH)

/** Catégories existantes, sans doublon, par ordre alphabétique français. */
fun getCategories(products: List<Product>): List<String> {
    val seen = LinkedHashMap<String, String>()
    for (product in products) {
        val key = categoryKey(product.category)
        val category = product.category
        if (category != null && key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = category
        }
    }
    val collator = frenchCollator()
    return seen.values.sortedWith(Comparator { a, b -> collator.compare(a, b) })
}

/**
 * Catégorie saisie -> catégorie à enregistrer. Si elle existe déjà (à la casse près),
 * on reprend l'écriture existante au lieu de créer un doublon.
 */
fun resolveCategory(input: String, products: List<Product>): String? {
    val cleaned = cleanCategory(input) ?: return null
    val key = categoryKey(cleaned)
    return getCategories(products).firstOrNull { categoryKey(it) == key } ?: cleaned
}

enum class ProductFilter(val id: String) { All("all"), Available("available"), Low("low"), Out("out") }

enum class StockStatus { Normal, Low, Out }

fun stockStatusOf(product: Product): StockStatus = when {
    product.stock <= 0 -> StockStatus.Out
    product.stockThreshold > 0 && product.stock <= product.stockThreshold -> StockStatus.Low
    else -> StockStatus.Normal
}

/** Recherche (nom, catégorie, code-barres, avec ou sans espaces) + filtre de stock. */
fun filterProducts(products: List<Product>, query: String, filter: ProductFilter): List<Product> {
    val search = query.jsTrim().lowercase()
    val searchCode = search.replace(JS_WHITESPACE_RUN, "")

    return products.filter { product ->
        val name = product.name.lowercase()
        val barcode = product.barcode?.lowercase() ?: ""
        val category = (product.category ?: "").lowercase()

        val matches = name.contains(search) ||
            category.contains(search) ||
            barcode.contains(search) ||
            (searchCode.isNotEmpty() && barcode.contains(searchCode))

        if (!matches) {
            false
        } else {
            when (filter) {
                ProductFilter.All -> true
                ProductFilter.Available -> product.stock > 0
                ProductFilter.Low ->
                    product.stock > 0 && product.stockThreshold > 0 && product.stock <= product.stockThreshold
                ProductFilter.Out -> product.stock <= 0
            }
        }
    }
}

data class ProductGroup(val key: String, val name: String, val products: List<Product>)

/** Groupes alphabétiques ; « Sans catégorie » toujours en dernier. */
fun groupByCategory(products: List<Product>): List<ProductGroup> {
    val keys = ArrayList<String>()
    val names = HashMap<String, String>()
    val members = HashMap<String, MutableList<Product>>()

    for (product in products) {
        val key = categoryKey(product.category)
        if (!members.containsKey(key)) {
            keys.add(key)
            names[key] = product.category ?: UNCATEGORIZED_LABEL
            members[key] = ArrayList()
        }
        members[key]!!.add(product)
    }

    val collator = frenchCollator()
    return keys
        .map { ProductGroup(it, names[it] ?: UNCATEGORIZED_LABEL, members[it] ?: emptyList()) }
        .sortedWith(
            Comparator { a, b ->
                when {
                    a.key.isEmpty() && b.key.isEmpty() -> 0
                    a.key.isEmpty() -> 1
                    b.key.isEmpty() -> -1
                    else -> collator.compare(a.name, b.name)
                }
            }
        )
}

/** Équivalent de new Intl.NumberFormat("fr-FR").format(price) + " FCFA" (3 décimales max). */
fun formatPrice(price: Double): String {
    val rounded = BigDecimal(price.toString()).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()
    val parts = rounded.toPlainString().split(".")
    val integer = formatNumber(parts[0].toDouble())
    val decimals = if (parts.size > 1) "," + parts[1] else ""
    return "$integer$decimals FCFA"
}

enum class ProductsViewMode(val id: String) { GridView("grid"), ListView("list") }

enum class ProductsGrouping(val id: String) { All("all"), Category("category") }

/** Préférences d'affichage de la page Produits (texte brut, comme l'ancienne app). */
class ProductPrefsRepository(private val store: KeyValueStore) {
    companion object {
        const val VIEW_KEY = "ejden_products_view"
        const val GROUPING_KEY = "ejden_products_grouping"
    }

    fun getViewMode(): ProductsViewMode =
        ProductsViewMode.entries.firstOrNull { it.id == store.get(VIEW_KEY) } ?: ProductsViewMode.GridView

    fun saveViewMode(mode: ProductsViewMode): Boolean = store.set(VIEW_KEY, mode.id)

    fun getGrouping(): ProductsGrouping =
        ProductsGrouping.entries.firstOrNull { it.id == store.get(GROUPING_KEY) } ?: ProductsGrouping.All

    fun saveGrouping(grouping: ProductsGrouping): Boolean = store.set(GROUPING_KEY, grouping.id)
}
