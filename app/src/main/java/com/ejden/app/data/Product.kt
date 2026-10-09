package com.ejden.app.data

import org.json.JSONObject
import kotlin.math.floor

data class Product(
    val id: String,
    val name: String,
    val purchasePrice: Double,
    val salePrice: Double,
    val stock: Int,
    val stockThreshold: Int,
    val barcode: String?,
    val photo: String?,
    val category: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("purchasePrice", purchasePrice)
        put("salePrice", salePrice)
        put("stock", stock)
        put("stockThreshold", stockThreshold)
        put("barcode", barcode ?: JSONObject.NULL)
        put("photo", photo ?: JSONObject.NULL)
        put("category", category ?: JSONObject.NULL)
    }
}

private val SAFE_PHOTO = Regex("data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+")
private val CONTROL_CHARS = Regex("[\\u0000-\\u001F\\u007F]")

fun isSafePhoto(value: Any?): Boolean =
    value is String && value.length <= Limits.PHOTO_MAX && SAFE_PHOTO.matches(value)

fun cleanBarcode(value: Any?): String? {
    if (value !is String) return null
    val code = value.replace(JS_WHITESPACE_RUN, "").take(Limits.BARCODE_MAX)
    return if (code.isEmpty()) null else code
}

/** Nom de catégorie propre : sans caractères de contrôle, 40 caractères max. */
fun cleanCategory(value: Any?): String? {
    if (value !is String) return null
    val category = value
        .replace(CONTROL_CHARS, " ")
        .replace(JS_WHITESPACE_RUN, " ")
        .jsTrim()
        .take(Limits.CATEGORY_MAX)
        .jsTrim()
    return if (category.isEmpty()) null else category
}

private fun clamp(n: Double, max: Double): Double = minOf(maxOf(n, 0.0), max)

/**
 * Valide ET nettoie un produit (venant du disque ou d'un formulaire).
 * Les anciens produits (barcode/photo absents) sont acceptés.
 */
fun normalizeProduct(value: Any?): Product? {
    val p = value as? JSONObject ?: return null

    val id = p.opt("id") as? String ?: return null
    if (id.isEmpty() || id.length > 100) return null

    val name = (p.opt("name") as? String)?.jsTrim()?.take(Limits.NAME_MAX) ?: ""
    val purchase = finiteNumber(p.opt("purchasePrice"))
    val sale = finiteNumber(p.opt("salePrice"))
    val stock = finiteNumber(p.opt("stock"))
    if (name.isEmpty() || purchase == null || sale == null || stock == null) return null

    val threshold = finiteNumber(p.opt("stockThreshold")) ?: 0.0

    return Product(
        id = id,
        name = name,
        purchasePrice = clamp(purchase, Limits.MONEY_MAX),
        salePrice = clamp(sale, Limits.MONEY_MAX),
        stock = floor(clamp(stock, Limits.STOCK_MAX)).toInt(),
        stockThreshold = floor(clamp(threshold, Limits.STOCK_MAX)).toInt(),
        barcode = cleanBarcode(p.opt("barcode")),
        photo = (p.opt("photo") as? String)?.takeIf { isSafePhoto(it) },
        category = cleanCategory(p.opt("category"))
    )
}

class ProductRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_products"
    }

    fun getProducts(): List<Product> = readList(store, KEY, ::normalizeProduct)

    fun getProductById(productId: String): Product? =
        getProducts().firstOrNull { it.id == productId }

    fun saveProducts(products: List<Product>): Boolean =
        writeList(store, KEY, products.map { it.toJson() })

    fun addProduct(product: Product): Boolean {
        val clean = normalizeProduct(product.toJson()) ?: return false
        return saveProducts(getProducts() + clean)
    }

    fun updateProduct(product: Product): Boolean {
        val clean = normalizeProduct(product.toJson()) ?: return false
        val products = getProducts().toMutableList()
        val index = products.indexOfFirst { it.id == clean.id }
        if (index == -1) return false
        products[index] = clean
        return saveProducts(products)
    }

    fun deleteProduct(productId: String): Boolean {
        val products = getProducts()
        val remaining = products.filter { it.id != productId }
        if (remaining.size == products.size) return false
        return saveProducts(remaining)
    }
}
