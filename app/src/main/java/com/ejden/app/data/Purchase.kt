package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.floor

const val PURCHASE_ITEMS_MAX = 50

private fun isWholeAmount(value: Double): Boolean = value.isFinite() && value == floor(value)

data class PurchaseItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    /** Prix d'achat unitaire de cette livraison. */
    val unitCost: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("productId", productId)
        put("productName", productName)
        put("quantity", quantity)
        put("unitCost", unitCost)
    }
}

data class Purchase(
    val id: String,
    val supplierId: String,
    /** Gardé : l'historique reste lisible si la fiche est supprimée. */
    val supplierName: String,
    val items: List<PurchaseItem>,
    val total: Double,
    /** Payé à la livraison (le reste est dû). */
    val paid: Double,
    val note: String,
    val createdAt: String,
    val cancelledAt: String? = null
) {
    val isCancelled: Boolean get() = cancelledAt != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("supplierId", supplierId)
        put("supplierName", supplierName)
        val array = JSONArray()
        for (item in items) array.put(item.toJson())
        put("items", array)
        put("total", total)
        put("paid", paid)
        put("note", note)
        put("createdAt", createdAt)
        if (cancelledAt != null) put("cancelledAt", cancelledAt)
    }
}

fun normalizePurchaseItem(value: Any?): PurchaseItem? {
    val i = value as? JSONObject ?: return null
    val productId = i.opt("productId") as? String
    val productName = cleanLabel(i.opt("productName"), Limits.NAME_MAX)
    val unitCost = cleanAmount(i.opt("unitCost"))
    val quantity = finiteNumber(i.opt("quantity"))

    if (productId == null || productId.isEmpty() || productId.length > 100 ||
        productName.isEmpty() || unitCost == null || quantity == null ||
        !isWholeAmount(quantity) || quantity < 1.0 || quantity > Limits.STOCK_MAX
    ) {
        return null
    }
    return PurchaseItem(productId, productName, quantity.toInt(), unitCost)
}

fun normalizePurchase(value: Any?): Purchase? {
    val p = value as? JSONObject ?: return null
    val id = p.opt("id") as? String
    val supplierId = p.opt("supplierId") as? String
    val supplierName = cleanLabel(p.opt("supplierName"), ModuleLimits.TEXT_MAX)
    val createdAt = validDate(p.opt("createdAt"))
    val rawItems = p.opt("items") as? JSONArray

    if (id == null || id.isEmpty() || id.length > 100 ||
        supplierId == null || supplierId.isEmpty() || supplierId.length > 100 ||
        supplierName.isEmpty() || createdAt == null || rawItems == null ||
        rawItems.length() < 1 || rawItems.length() > PURCHASE_ITEMS_MAX
    ) {
        return null
    }

    val items = ArrayList<PurchaseItem>()
    for (index in 0 until rawItems.length()) {
        val item = normalizePurchaseItem(rawItems.opt(index)) ?: return null
        items.add(item)
    }

    val total = items.sumOf { it.quantity * it.unitCost }
    val paid = finiteNumber(p.opt("paid"))

    if (finiteNumber(p.opt("total")) != total ||
        paid == null || !isWholeAmount(paid) || paid < 0.0 || paid > total
    ) {
        return null
    }

    val cancelledAt = (p.opt("cancelledAt") as? String)?.let { validDate(it) }

    return Purchase(
        id = id,
        supplierId = supplierId,
        supplierName = supplierName,
        items = items,
        total = total,
        paid = paid,
        note = cleanLabel(p.opt("note"), ModuleLimits.TEXT_MAX),
        createdAt = createdAt,
        cancelledAt = cancelledAt
    )
}

data class PurchaseLine(val productId: String, val quantity: Int, val unitCost: Double)

data class PurchaseInput(
    val supplierId: String,
    val lines: List<PurchaseLine>,
    val paid: Double,
    val note: String,
    /** Met à jour le prix d'achat des produits avec celui de cette livraison. */
    val updatePrices: Boolean
)

enum class PurchaseRejection {
    SupplierNotFound, NoItems, InvalidItem, UnknownProduct,
    DuplicateProduct, InvalidPaid, TooHigh, Storage
}

sealed class RecordPurchaseResult {
    data class Ok(val purchase: Purchase) : RecordPurchaseResult()
    data class Rejected(val reason: PurchaseRejection) : RecordPurchaseResult()
}

class PurchaseRepository(
    private val store: KeyValueStore,
    private val suppliers: SupplierRepository,
    private val products: ProductRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    companion object {
        const val KEY = "ejden_purchases"
    }

    fun getPurchases(): List<Purchase> = readList(store, KEY, ::normalizePurchase)

    private fun save(purchases: List<Purchase>): Boolean =
        writeList(store, KEY, purchases.map { it.toJson() })

    private fun reject(reason: PurchaseRejection): RecordPurchaseResult =
        RecordPurchaseResult.Rejected(reason)

    /**
     * Un achat augmente le stock des produits livrés. Ce qui n'est pas payé
     * à la livraison devient une dette envers le fournisseur.
     */
    fun recordPurchase(input: PurchaseInput): RecordPurchaseResult {
        val supplier = suppliers.getSupplierById(input.supplierId)
            ?: return reject(PurchaseRejection.SupplierNotFound)

        if (input.lines.isEmpty() || input.lines.size > PURCHASE_ITEMS_MAX) {
            return reject(PurchaseRejection.NoItems)
        }

        val original = products.getProducts()
        val updated = original.toMutableList()
        val seen = HashSet<String>()
        val items = ArrayList<PurchaseItem>()

        for (line in input.lines) {
            val unitCost = cleanAmount(line.unitCost)
            if (line.quantity < 1 || unitCost == null) {
                return reject(PurchaseRejection.InvalidItem)
            }

            val index = updated.indexOfFirst { it.id == line.productId }
            if (index == -1) return reject(PurchaseRejection.UnknownProduct)
            if (!seen.add(line.productId)) return reject(PurchaseRejection.DuplicateProduct)

            val product = updated[index]
            val stock = product.stock.toLong() + line.quantity.toLong()
            if (stock.toDouble() > Limits.STOCK_MAX) return reject(PurchaseRejection.TooHigh)

            updated[index] = product.copy(
                stock = stock.toInt(),
                purchasePrice = if (input.updatePrices) unitCost else product.purchasePrice
            )
            items.add(PurchaseItem(product.id, product.name, line.quantity, unitCost))
        }

        val total = items.sumOf { it.quantity * it.unitCost }
        if (total > ModuleLimits.AMOUNT_MAX) return reject(PurchaseRejection.TooHigh)

        val paid = input.paid
        if (!isWholeAmount(paid) || paid < 0.0 || paid > total) {
            return reject(PurchaseRejection.InvalidPaid)
        }

        val purchase = Purchase(
            id = newId(),
            supplierId = supplier.id,
            supplierName = supplier.name,
            items = items,
            total = total,
            paid = paid,
            note = cleanLabel(input.note, ModuleLimits.TEXT_MAX),
            createdAt = toIso(now())
        )

        if (!products.saveProducts(updated)) return reject(PurchaseRejection.Storage)

        if (!save(getPurchases() + purchase)) {
            products.saveProducts(original)
            return reject(PurchaseRejection.Storage)
        }
        return RecordPurchaseResult.Ok(purchase)
    }
}
