package com.ejden.app.data

private const val PRODUCTS_DAY_MS = 86_400_000L

data class AbcClass(val count: Int, val sharePct: Double)
data class AbcTop(val name: String, val amount: Double, val sharePct: Double)
data class AbcAnalysis(
    val a: AbcClass,
    val b: AbcClass,
    val c: AbcClass,
    val unsold: Int,
    val topA: List<AbcTop>
)

data class CategoryShare(val name: String, val amount: Double, val sharePct: Double, val ratio: Double)
data class ProductMove(val name: String, val now: Double, val before: Double, val change: Double)
data class PriceAlert(val name: String, val purchasePrice: Double, val salePrice: Double, val marginPct: Double)

data class ProductsData(
    val abc: AbcAnalysis,
    val categories: List<CategoryShare>,
    val risers: List<ProductMove>,
    val fallers: List<ProductMove>,
    val lossMakers: List<PriceAlert>,
    val thin: List<PriceAlert>
)

private class ProductAcc(val name: String) {
    var amount = 0.0
    var quantity = 0.0
}

/** Retourne null s'il n'y a aucune vente sur la période (message vide du HTML). */
fun computeProducts(
    rows: List<StatRow>,
    products: List<Product>,
    horizonDays: Int,
    nowMillis: Long
): ProductsData? {
    val start = nowMillis - horizonDays * PRODUCTS_DAY_MS
    val prevStart = start - horizonDays * PRODUCTS_DAY_MS
    val current = LinkedHashMap<String, ProductAcc>()
    val previous = LinkedHashMap<String, Double>()

    for (row in rows) {
        if (row.at < prevStart) continue
        for (item in row.sale.items) {
            if (row.at >= start) {
                val e = current.getOrPut(item.productId) { ProductAcc(item.productName) }
                e.amount += item.total
                e.quantity += item.quantity
            } else {
                previous[item.productId] = (previous[item.productId] ?: 0.0) + item.quantity
            }
        }
    }

    val ranked = current.values.sortedByDescending { it.amount }
    if (ranked.isEmpty()) return null
    val totalAmount = ranked.sumOf { it.amount }

    /* ABC (Pareto) */
    val a = ArrayList<ProductAcc>()
    val b = ArrayList<ProductAcc>()
    val c = ArrayList<ProductAcc>()
    var cumulative = 0.0
    for (entry in ranked) {
        val before = cumulative / totalAmount
        cumulative += entry.amount
        when {
            before < 0.8 -> a.add(entry)
            before < 0.95 -> b.add(entry)
            else -> c.add(entry)
        }
    }
    fun share(list: List<ProductAcc>): Double = list.sumOf { it.amount } / totalAmount * 100
    val unsold = products.count { !current.containsKey(it.id) }
    val abc = AbcAnalysis(
        a = AbcClass(a.size, share(a)),
        b = AbcClass(b.size, share(b)),
        c = AbcClass(c.size, share(c)),
        unsold = unsold,
        topA = a.take(5).map { AbcTop(it.name, it.amount, it.amount / totalAmount * 100) }
    )

    /* Catégories */
    val byProduct = products.associateBy { it.id }
    val categories = LinkedHashMap<String, Double>()
    for ((id, v) in current) {
        val name = byProduct[id]?.category ?: "Sans catégorie"
        categories[name] = (categories[name] ?: 0.0) + v.amount
    }
    val categoryRows = categories.entries.sortedByDescending { it.value }.take(8)
    val categoryShares = if (categoryRows.size > 1) {
        categoryRows.map {
            CategoryShare(it.key, it.value, it.value / totalAmount * 100, it.value / categoryRows[0].value)
        }
    } else {
        emptyList()
    }

    /* Hausses et baisses de volumes */
    val moves = ArrayList<ProductMove>()
    for ((id, v) in current) {
        val before = previous[id] ?: 0.0
        if (before >= 3 || v.quantity >= 3) {
            moves.add(
                ProductMove(
                    v.name, v.quantity, before,
                    if (before > 0) (v.quantity - before) / before * 100 else 100.0
                )
            )
        }
    }
    for ((id, before) in previous) {
        if (!current.containsKey(id) && before >= 3) {
            moves.add(ProductMove(byProduct[id]?.name ?: "Produit supprimé", 0.0, before, -100.0))
        }
    }
    val risers = moves.filter { it.change > 10 }.sortedByDescending { it.change }.take(4)
    val fallers = moves.filter { it.change < -10 }.sortedBy { it.change }.take(4)

    /* Marges du catalogue */
    fun margin(p: Product): Double = (p.salePrice - p.purchasePrice) / p.salePrice * 100
    val priced = products.filter { it.purchasePrice > 0 && it.salePrice > 0 }
    val loss = priced.filter { it.salePrice < it.purchasePrice }
    val thin = priced
        .filter { it.salePrice >= it.purchasePrice && margin(it) < 10 }
        .sortedBy { margin(it) }

    return ProductsData(
        abc = abc,
        categories = categoryShares,
        risers = risers,
        fallers = fallers,
        lossMakers = loss.take(5).map { PriceAlert(it.name, it.purchasePrice, it.salePrice, margin(it)) },
        thin = thin.take(5).map { PriceAlert(it.name, it.purchasePrice, it.salePrice, margin(it)) }
    )
}
