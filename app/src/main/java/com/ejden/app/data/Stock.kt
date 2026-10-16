package com.ejden.app.data

import org.json.JSONObject
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor

enum class StockMovementType(val id: String) {
    In("in"), Out("out"), Adjust("adjust");

    companion object {
        fun fromId(value: Any?): StockMovementType? = entries.firstOrNull { it.id == value }
    }
}

/** Motifs proposés pour chaque type de mouvement. */
val STOCK_REASONS: Map<StockMovementType, List<String>> = mapOf(
    StockMovementType.In to listOf("Réapprovisionnement", "Retour client", "Autre"),
    StockMovementType.Out to listOf("Perte", "Casse", "Produit périmé", "Usage personnel", "Autre"),
    StockMovementType.Adjust to listOf("Inventaire")
)

val STOCK_TAB_LABELS: Map<StockMovementType, String> = mapOf(
    StockMovementType.In to "Entrée",
    StockMovementType.Out to "Sortie",
    StockMovementType.Adjust to "Inventaire"
)

const val STOCK_MOVEMENTS_KEPT = 2000

data class StockMovement(
    val id: String,
    val productId: String,
    val productName: String,
    val type: StockMovementType,
    /** Variation : positive (entrée), négative (sortie) ou écart d'inventaire. */
    val quantity: Int,
    val before: Int,
    val after: Int,
    val reason: String,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("productId", productId)
        put("productName", productName)
        put("type", type.id)
        put("quantity", quantity)
        put("before", before)
        put("after", after)
        put("reason", reason)
        put("createdAt", createdAt)
    }
}

/** Quantité de stock valide : entier entre 0 et 1 000 000. */
private fun stockQuantity(value: Any?): Int? {
    val n = (value as? Number)?.toDouble() ?: return null
    if (!n.isFinite() || n != floor(n) || n < 0.0 || n > Limits.STOCK_MAX) return null
    return n.toInt()
}

fun normalizeStockMovement(value: Any?): StockMovement? {
    val m = value as? JSONObject ?: return null
    val productName = cleanLabel(m.opt("productName"), Limits.NAME_MAX)
    val reason = cleanLabel(m.opt("reason"), ModuleLimits.TEXT_MAX)
    val createdAt = validDate(m.opt("createdAt"))
    val id = m.opt("id") as? String
    val productId = m.opt("productId") as? String
    val type = StockMovementType.fromId(m.opt("type"))
    val before = stockQuantity(m.opt("before"))
    val after = stockQuantity(m.opt("after"))
    val quantity = (m.opt("quantity") as? Number)?.toDouble()

    if (id == null || id.isEmpty() || id.length > 100 ||
        productId == null || productId.isEmpty() || productId.length > 100 ||
        productName.isEmpty() || reason.isEmpty() || createdAt == null ||
        type == null || before == null || after == null ||
        quantity == null || !quantity.isFinite() || quantity != floor(quantity) || quantity == 0.0 ||
        (after - before).toDouble() != quantity
    ) {
        return null
    }
    return StockMovement(id, productId, productName, type, quantity.toInt(), before, after, reason, createdAt)
}

enum class StockFailure { NotFound, InvalidQuantity, TooMuch, TooHigh, Unchanged, InvalidReason, Storage }

sealed class StockMovementResult {
    data class Ok(val movement: StockMovement) : StockMovementResult()
    data class Failed(val reason: StockFailure) : StockMovementResult()
}

fun stockErrorMessage(reason: StockFailure): String = when (reason) {
    StockFailure.NotFound -> "Produit introuvable."
    StockFailure.InvalidQuantity -> "Quantité invalide : un nombre entier est attendu."
    StockFailure.TooMuch -> "Quantité supérieure au stock disponible."
    StockFailure.TooHigh -> "Quantité trop grande (${formatNumber(Limits.STOCK_MAX)} max)."
    StockFailure.Unchanged -> "Le stock compté est identique au stock actuel."
    StockFailure.InvalidReason -> "Indiquez un motif."
    StockFailure.Storage -> "Impossible d'enregistrer le mouvement."
}

enum class StockSource { Manual, Sale, SaleCancel, Purchase, PurchaseCancel }

/** Une ligne de l'historique : variation signée, libellé, stock après (si connu). */
data class StockHistoryEntry(
    val id: String,
    val productId: String,
    val productName: String,
    val delta: Double,
    val label: String,
    val source: StockSource,
    val createdAt: String,
    val after: Int?
)

data class StockValue(val cost: Double, val retail: Double)

fun getStockValue(products: List<Product>): StockValue = StockValue(
    products.sumOf { it.stock * it.purchasePrice },
    products.sumOf { it.stock * it.salePrice }
)

/**
 * extraHistory : lignes ajoutées par d'autres modules (achats fournisseurs...),
 * filtrées sur un produit si un identifiant est donné.
 */
class StockRepository(
    private val store: KeyValueStore,
    private val products: ProductRepository,
    private val sales: SaleRepository,
    private val now: () -> Instant = { Instant.now() },
    private val extraHistory: (String?) -> List<StockHistoryEntry> = { emptyList() }
) {
    companion object {
        const val KEY = "ejden_stock_movements"
    }

    fun getMovements(): List<StockMovement> = readList(store, KEY, ::normalizeStockMovement)

    /**
     * Enregistre une entrée, une sortie ou un inventaire (quantité comptée).
     * Le stock du produit et l'historique sont écrits ensemble : si l'un échoue, tout est remis.
     */
    fun recordMovement(
        productId: String,
        type: StockMovementType,
        quantity: Double,
        reason: String
    ): StockMovementResult {
        val list = products.getProducts()
        val index = list.indexOfFirst { it.id == productId }
        if (index == -1) return StockMovementResult.Failed(StockFailure.NotFound)
        val product = list[index]

        val cleanReason = cleanLabel(reason, ModuleLimits.TEXT_MAX)
        if (cleanReason.isEmpty()) return StockMovementResult.Failed(StockFailure.InvalidReason)

        val minimum = if (type == StockMovementType.Adjust) 0.0 else 1.0
        if (!quantity.isFinite() || quantity != floor(quantity) || quantity < minimum) {
            return StockMovementResult.Failed(StockFailure.InvalidQuantity)
        }

        val after: Int = when (type) {
            StockMovementType.In -> {
                val total = product.stock + quantity
                if (total > Limits.STOCK_MAX) return StockMovementResult.Failed(StockFailure.TooHigh)
                total.toInt()
            }
            StockMovementType.Out -> {
                if (quantity > product.stock) return StockMovementResult.Failed(StockFailure.TooMuch)
                (product.stock - quantity).toInt()
            }
            StockMovementType.Adjust -> {
                if (quantity > Limits.STOCK_MAX) return StockMovementResult.Failed(StockFailure.TooHigh)
                if (quantity.toInt() == product.stock) return StockMovementResult.Failed(StockFailure.Unchanged)
                quantity.toInt()
            }
        }

        val movement = StockMovement(
            id = newId(),
            productId = product.id,
            productName = product.name,
            type = type,
            quantity = after - product.stock,
            before = product.stock,
            after = after,
            reason = cleanReason,
            createdAt = toIso(now())
        )

        val updated = list.toMutableList()
        updated[index] = product.copy(stock = after)
        if (!products.saveProducts(updated)) return StockMovementResult.Failed(StockFailure.Storage)

        val movements = (getMovements() + movement).takeLast(STOCK_MOVEMENTS_KEPT)
        if (!writeList(store, KEY, movements.map { it.toJson() })) {
            products.saveProducts(list)
            return StockMovementResult.Failed(StockFailure.Storage)
        }
        return StockMovementResult.Ok(movement)
    }

    /** Historique (tous produits ou un seul) : mouvements, ventes et annulations, récents d'abord. */
    fun getHistory(productId: String? = null): List<StockHistoryEntry> {
        val entries = ArrayList<StockHistoryEntry>()

        for (m in getMovements()) {
            if (productId != null && m.productId != productId) continue
            entries.add(
                StockHistoryEntry(
                    "m-" + m.id, m.productId, m.productName, m.quantity.toDouble(),
                    m.reason, StockSource.Manual, m.createdAt, m.after
                )
            )
        }

        for (sale in sales.getSales()) {
            val number = sale.id.takeLast(6).uppercase()
            val cancelled = sale.cancelledAt
            sale.items.forEachIndexed { position, item ->
                if (productId != null && item.productId != productId) return@forEachIndexed
                entries.add(
                    StockHistoryEntry(
                        "s-" + sale.id + "-" + position, item.productId, item.productName, -item.quantity,
                        "Vente N° $number", StockSource.Sale, sale.createdAt, null
                    )
                )
                if (!cancelled.isNullOrEmpty()) {
                    entries.add(
                        StockHistoryEntry(
                            "c-" + sale.id + "-" + position, item.productId, item.productName, item.quantity,
                            "Annulation vente N° $number", StockSource.SaleCancel, cancelled, null
                        )
                    )
                }
            }
        }

        entries.addAll(extraHistory(productId))
        return entries.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
    }
}

enum class StockFilter(val id: String) {
    All("all"), Low("low"), Out("out");

    companion object {
        fun fromId(value: Any?): StockFilter? = entries.firstOrNull { it.id == value }
    }
}

private fun statusOrder(status: StockStatus): Int = when (status) {
    StockStatus.Out -> 0
    StockStatus.Low -> 1
    StockStatus.Normal -> 2
}

/** Produits de la page Stock : filtrés, ruptures d'abord, puis stock faible, puis le reste. */
fun filterStockProducts(products: List<Product>, query: String, filter: StockFilter): List<Product> {
    val search = query.jsTrim().lowercase()
    val searchCode = search.replace(JS_WHITESPACE_RUN, "")
    val collator = Collator.getInstance(Locale.FRENCH)

    return products
        .filter { product ->
            val status = stockStatusOf(product)
            val statusOk = when (filter) {
                StockFilter.All -> true
                StockFilter.Low -> status == StockStatus.Low
                StockFilter.Out -> status == StockStatus.Out
            }
            statusOk && (
                search.isEmpty() ||
                    product.name.lowercase().contains(search) ||
                    (product.barcode ?: "").contains(searchCode) ||
                    (product.category ?: "").lowercase().contains(search)
                )
        }
        .sortedWith(
            Comparator { a, b ->
                val byStatus = statusOrder(stockStatusOf(a)) - statusOrder(stockStatusOf(b))
                if (byStatus != 0) byStatus else collator.compare(a.name, b.name)
            }
        )
}

/** « + 12 » ou « − 3 » (signe moins typographique). */
fun signedQuantity(value: Double): String =
    (if (value > 0.0) "+" else "\u2212") + " " + formatNumber(abs(value))

fun stockDateTime(createdAt: String, zone: ZoneId): String {
    if (parseDateMillis(createdAt) == null) return ""
    return shortDateText(createdAt, zone) + " " + timeText(createdAt, zone)
}
