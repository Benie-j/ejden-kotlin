package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.floor

enum class PaymentMethod(val id: String) {
    Cash("cash"),
    MobileMoney("mobile_money"),
    Card("card"),
    Other("other"),
    Credit("credit");

    companion object {
        fun fromId(value: Any?): PaymentMethod? = entries.firstOrNull { it.id == value }
    }
}

data class SaleItem(
    val productId: String,
    val productName: String,
    val unitPrice: Double,
    val unitCost: Double?,
    val quantity: Double,
    val total: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("productId", productId)
        put("productName", productName)
        put("unitPrice", unitPrice)
        if (unitCost != null) put("unitCost", unitCost)
        put("quantity", quantity)
        put("total", total)
    }
}

data class Sale(
    val id: String,
    val createdAt: String,
    val items: List<SaleItem>,
    val subtotal: Double,
    val discount: Double,
    val total: Double,
    val paymentMethod: PaymentMethod,
    val amountPaid: Double,
    val change: Double,
    val remaining: Double,
    val customerId: String?,
    val borrowerName: String?,
    val cancelledAt: String? = null
) {
    val isCancelled: Boolean get() = !cancelledAt.isNullOrEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("createdAt", createdAt)
        val array = JSONArray()
        for (item in items) array.put(item.toJson())
        put("items", array)
        put("subtotal", subtotal)
        put("discount", discount)
        put("total", total)
        put("paymentMethod", paymentMethod.id)
        put("amountPaid", amountPaid)
        put("change", change)
        put("remaining", remaining)
        put("customerId", customerId ?: JSONObject.NULL)
        put("borrowerName", borrowerName ?: JSONObject.NULL)
        if (cancelledAt != null) put("cancelledAt", cancelledAt)
    }
}

fun parseSaleItem(value: Any?): SaleItem? {
    val i = value as? JSONObject ?: return null
    val productId = i.opt("productId") as? String ?: return null
    val productName = i.opt("productName") as? String ?: return null
    val unitPrice = finiteNumber(i.opt("unitPrice")) ?: return null
    val rawCost = i.opt("unitCost")
    val unitCost: Double? = if (rawCost == null) null else (finiteNumber(rawCost) ?: return null)
    val quantity = finiteNumber(i.opt("quantity")) ?: return null
    if (quantity <= 0.0) return null
    val total = finiteNumber(i.opt("total")) ?: return null
    return SaleItem(productId, productName, unitPrice, unitCost, quantity, total)
}

/** Valide une vente lue sur le disque ; null si elle est inutilisable. */
fun parseSale(value: Any?): Sale? {
    val s = value as? JSONObject ?: return null
    val id = s.opt("id") as? String ?: return null
    val createdAt = s.opt("createdAt") as? String ?: return null
    val rawItems = s.opt("items") as? JSONArray ?: return null

    val items = ArrayList<SaleItem>()
    for (index in 0 until rawItems.length()) {
        items.add(parseSaleItem(rawItems.opt(index)) ?: return null)
    }

    val subtotal = finiteNumber(s.opt("subtotal")) ?: return null
    val discount = finiteNumber(s.opt("discount")) ?: return null
    val total = finiteNumber(s.opt("total")) ?: return null
    val method = PaymentMethod.fromId(s.opt("paymentMethod")) ?: return null
    val amountPaid = finiteNumber(s.opt("amountPaid")) ?: return null
    val change = finiteNumber(s.opt("change")) ?: return null
    val remaining = finiteNumber(s.opt("remaining")) ?: return null

    val customer = s.opt("customerId")
    if (!(customer is String || customer === JSONObject.NULL)) return null
    val borrower = s.opt("borrowerName")
    if (!(borrower is String || borrower === JSONObject.NULL)) return null
    val cancelled = s.opt("cancelledAt")
    if (cancelled != null && cancelled !is String) return null

    return Sale(
        id, createdAt, items, subtotal, discount, total, method,
        amountPaid, change, remaining,
        customer as? String, borrower as? String, cancelled as? String
    )
}

sealed class RecordSaleResult {
    data object Ok : RecordSaleResult()
    data class OutOfStock(val productName: String) : RecordSaleResult()
    data object StorageError : RecordSaleResult()
}

sealed class CancelSaleResult {
    data class Ok(val missingProducts: Int) : CancelSaleResult()
    data object NotFound : CancelSaleResult()
    data object AlreadyCancelled : CancelSaleResult()
    data object HasPayments : CancelSaleResult()
    data object StorageError : CancelSaleResult()
}

data class SalesSummary(val salesCount: Int, val revenue: Double, val margin: Double?)

private fun normalizeBarcode(value: String): String =
    value.replace(JS_WHITESPACE_RUN, "").jsTrim()

fun ProductRepository.findProductByBarcode(barcode: String): Product? {
    val code = normalizeBarcode(barcode)
    if (code.isEmpty()) return null
    return getProducts().firstOrNull { p ->
        p.barcode != null && normalizeBarcode(p.barcode) == code
    }
}

class SaleRepository(
    private val store: KeyValueStore,
    private val products: ProductRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    companion object {
        const val KEY = "ejden_sales"
    }

    fun getSales(): List<Sale> = readList(store, KEY, ::parseSale)

    fun saveSales(sales: List<Sale>): Boolean =
        writeList(store, KEY, sales.map { it.toJson() })

    fun addSale(sale: Sale): Boolean = saveSales(getSales() + sale)

    /** Vente + baisse du stock en une opération : si une écriture échoue, tout est annulé. */
    fun recordSale(sale: Sale): RecordSaleResult {
        val original = products.getProducts()

        for (item in sale.items) {
            val product = original.firstOrNull { it.id == item.productId }
            if (product == null || product.stock < item.quantity) {
                return RecordSaleResult.OutOfStock(product?.name ?: item.productName)
            }
        }

        val updated = original.map { product ->
            val sold = sale.items.filter { it.productId == product.id }.sumOf { it.quantity }
            if (sold > 0.0) product.copy(stock = floor(product.stock - sold).toInt()) else product
        }

        if (!products.saveProducts(updated)) return RecordSaleResult.StorageError

        if (!addSale(sale)) {
            products.saveProducts(original)
            return RecordSaleResult.StorageError
        }
        return RecordSaleResult.Ok
    }

    /** La vente reste dans l'historique (annulée), le stock est remis. */
    fun cancelSale(saleId: String): CancelSaleResult {
        val sales = getSales().toMutableList()
        val index = sales.indexOfFirst { it.id == saleId }
        if (index == -1) return CancelSaleResult.NotFound

        val sale = sales[index]
        if (sale.isCancelled) return CancelSaleResult.AlreadyCancelled

        if (readCreditPayments(store).any { it.saleId == sale.id && !it.isCancelled }) {
            return CancelSaleResult.HasPayments
        }

        val toRestore = LinkedHashMap<String, Double>()
        for (item in sale.items) {
            toRestore[item.productId] = (toRestore[item.productId] ?: 0.0) + item.quantity
        }

        val original = products.getProducts()
        val known = original.map { it.id }.toSet()
        val missing = toRestore.keys.count { it !in known }

        val restored = original.map { product ->
            val quantity = toRestore[product.id]
            if (quantity != null && quantity != 0.0) {
                val newStock = minOf(product.stock + quantity, Limits.STOCK_MAX)
                product.copy(stock = floor(newStock).toInt())
            } else {
                product
            }
        }

        if (!products.saveProducts(restored)) return CancelSaleResult.StorageError

        sales[index] = sale.copy(cancelledAt = toIso(now()))

        if (!saveSales(sales)) {
            products.saveProducts(original)
            return CancelSaleResult.StorageError
        }
        return CancelSaleResult.Ok(missing)
    }

    /** Ventes non annulées entre deux instants (en millisecondes, bornes incluses). */
    fun getSalesSummaryBetween(fromMillis: Long, toMillis: Long): SalesSummary {
        val periodSales = getSales().filter { sale ->
            val at = parseDateMillis(sale.createdAt)
            !sale.isCancelled && at != null && at >= fromMillis && at <= toMillis
        }

        var revenue = 0.0
        var margin = 0.0
        var marginKnown = false

        for (sale in periodSales) {
            revenue += sale.total
            if (sale.items.all { it.unitCost != null }) {
                val saleMargin = sale.items.sumOf { (it.unitPrice - (it.unitCost ?: 0.0)) * it.quantity }
                margin += saleMargin - sale.discount
                marginKnown = true
            }
        }

        return SalesSummary(periodSales.size, revenue, if (marginKnown) margin else null)
    }
}
