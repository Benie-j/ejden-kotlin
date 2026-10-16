package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor

const val ORDER_ITEMS_MAX = 50
const val WALK_IN_CLIENT = "Client de passage"

enum class OrderKind(val id: String) {
    Customer("customer"),
    Supplier("supplier");

    companion object {
        fun fromId(value: Any?): OrderKind? = entries.firstOrNull { it.id == value }
    }
}

/** « Prête » n'existe que pour les commandes clients. */
enum class OrderStatus(val id: String) {
    Pending("pending"),
    Ready("ready"),
    Completed("completed"),
    Cancelled("cancelled");

    companion object {
        fun fromId(value: Any?): OrderStatus? = entries.firstOrNull { it.id == value }
    }
}

private fun isWholeCount(value: Double): Boolean = value.isFinite() && value == floor(value)

data class OrderItem(
    val productId: String,
    val productName: String,
    val quantity: Int,
    /** Prix de vente (commande client) ou prix d'achat (commande fournisseur). */
    val unitPrice: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("productId", productId)
        put("productName", productName)
        put("quantity", quantity)
        put("unitPrice", unitPrice)
    }
}

data class Order(
    val id: String,
    val kind: OrderKind,
    /** Client ou fournisseur enregistré ; null = client de passage. */
    val partyId: String?,
    /** Gardé : l'historique reste lisible si la fiche est supprimée. */
    val partyName: String,
    val items: List<OrderItem>,
    val total: Double,
    val note: String,
    /** Date de livraison prévue (AAAA-MM-JJ), ou null. */
    val expectedDate: String?,
    val status: OrderStatus,
    val createdAt: String,
    val completedAt: String? = null,
    val cancelledAt: String? = null,
    /** Vente (commande client) ou achat (commande fournisseur) créé à la fin. */
    val linkId: String? = null
) {
    val isOpen: Boolean get() = status == OrderStatus.Pending || status == OrderStatus.Ready

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.id)
        put("partyId", partyId ?: JSONObject.NULL)
        put("partyName", partyName)
        val array = JSONArray()
        for (item in items) array.put(item.toJson())
        put("items", array)
        put("total", total)
        put("note", note)
        put("expectedDate", expectedDate ?: JSONObject.NULL)
        put("status", status.id)
        put("createdAt", createdAt)
        if (completedAt != null) put("completedAt", completedAt)
        if (cancelledAt != null) put("cancelledAt", cancelledAt)
        if (linkId != null) put("linkId", linkId)
    }
}

fun normalizeOrderItem(value: Any?): OrderItem? {
    val i = value as? JSONObject ?: return null
    val productId = i.opt("productId") as? String
    val productName = cleanLabel(i.opt("productName"), Limits.NAME_MAX)
    val unitPrice = cleanAmount(i.opt("unitPrice"))
    val quantity = finiteNumber(i.opt("quantity"))

    if (productId == null || productId.isEmpty() || productId.length > 100 ||
        productName.isEmpty() || unitPrice == null || quantity == null ||
        !isWholeCount(quantity) || quantity < 1.0 || quantity > Limits.STOCK_MAX
    ) {
        return null
    }
    return OrderItem(productId, productName, quantity.toInt(), unitPrice)
}

fun normalizeOrder(value: Any?): Order? {
    val o = value as? JSONObject ?: return null
    val id = o.opt("id") as? String
    val kind = OrderKind.fromId(o.opt("kind"))
    val status = OrderStatus.fromId(o.opt("status"))
    val partyName = cleanLabel(o.opt("partyName"), ModuleLimits.TEXT_MAX)
    val createdAt = validDate(o.opt("createdAt"))
    val rawItems = o.opt("items") as? JSONArray

    var partyId: String? = null
    if (!o.isNull("partyId")) {
        val raw = o.opt("partyId") as? String ?: return null
        if (raw.isEmpty() || raw.length > 100) return null
        partyId = raw
    }

    var expectedDate: String? = null
    if (!o.isNull("expectedDate")) {
        val raw = o.opt("expectedDate")
        if (!isValidDay(raw)) return null
        expectedDate = raw as String
    }

    if (id == null || id.isEmpty() || id.length > 100 || kind == null || status == null ||
        partyName.isEmpty() || createdAt == null || rawItems == null ||
        rawItems.length() < 1 || rawItems.length() > ORDER_ITEMS_MAX
    ) {
        return null
    }

    // Une commande fournisseur a toujours un fournisseur enregistré.
    if (kind == OrderKind.Supplier && (partyId == null || status == OrderStatus.Ready)) return null

    val items = ArrayList<OrderItem>()
    for (index in 0 until rawItems.length()) {
        val item = normalizeOrderItem(rawItems.opt(index)) ?: return null
        items.add(item)
    }

    val total = items.sumOf { it.quantity * it.unitPrice }
    if (finiteNumber(o.opt("total")) != total) return null

    return Order(
        id = id,
        kind = kind,
        partyId = partyId,
        partyName = partyName,
        items = items,
        total = total,
        note = cleanLabel(o.opt("note"), ModuleLimits.TEXT_MAX),
        expectedDate = expectedDate,
        status = status,
        createdAt = createdAt,
        completedAt = (o.opt("completedAt") as? String)?.takeIf { validDate(it) != null },
        cancelledAt = (o.opt("cancelledAt") as? String)?.takeIf { validDate(it) != null },
        linkId = (o.opt("linkId") as? String)?.takeIf { it.length <= 100 }
    )
}

data class OrderInputLine(val productId: String, val quantity: Int, val unitPrice: Double)

data class OrderInput(
    val kind: OrderKind,
    val partyId: String?,
    val lines: List<OrderInputLine>,
    val note: String,
    val expectedDate: String?
)

enum class OrderRejection {
    PartyNotFound, PartyRequired, NoItems, InvalidItem, UnknownProduct,
    DuplicateProduct, TooHigh, InvalidDate, Storage
}

sealed class AddOrderResult {
    data class Ok(val order: Order) : AddOrderResult()
    data class Rejected(val reason: OrderRejection) : AddOrderResult()
}

sealed class OrderChangeResult {
    data object Ok : OrderChangeResult()
    data object NotFound : OrderChangeResult()
    data object NotAllowed : OrderChangeResult()
    data object StorageError : OrderChangeResult()
}

data class OrdersSummary(val customerOpen: Int, val supplierOpen: Int, val overdue: Int)

/** Une commande en cours dont la date prévue est dépassée (fin de journée, heure locale). */
fun isOrderOverdue(
    order: Order,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): Boolean {
    val expected = order.expectedDate ?: return false
    if (!order.isOpen) return false
    val endOfDay = LocalDate.parse(expected).atTime(23, 59, 59, 999_000_000).atZone(zone).toInstant()
    return endOfDay.isBefore(now)
}

class OrderRepository(
    private val store: KeyValueStore,
    private val suppliers: SupplierRepository,
    private val clients: ClientRepository,
    private val products: ProductRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    companion object {
        const val KEY = "ejden_orders"
    }

    fun getOrders(): List<Order> = readList(store, KEY, ::normalizeOrder)

    fun getOrderById(orderId: String): Order? = getOrders().firstOrNull { it.id == orderId }

    private fun save(orders: List<Order>): Boolean =
        writeList(store, KEY, orders.map { it.toJson() })

    private fun reject(reason: OrderRejection): AddOrderResult = AddOrderResult.Rejected(reason)

    /** Enregistre une commande. Elle n'a aucun effet sur le stock ni la caisse tant qu'elle n'est pas livrée. */
    fun addOrder(input: OrderInput): AddOrderResult {
        var partyName = WALK_IN_CLIENT
        var partyId: String? = null
        val rawParty = input.partyId

        if (input.kind == OrderKind.Supplier) {
            if (rawParty.isNullOrEmpty()) return reject(OrderRejection.PartyRequired)
            val supplier = suppliers.getSupplierById(rawParty) ?: return reject(OrderRejection.PartyNotFound)
            partyId = supplier.id
            partyName = supplier.name
        } else if (!rawParty.isNullOrEmpty()) {
            val client = clients.getClientById(rawParty) ?: return reject(OrderRejection.PartyNotFound)
            partyId = client.id
            partyName = client.name
        }

        val expectedDate = input.expectedDate
        if (expectedDate != null && !isValidDay(expectedDate)) return reject(OrderRejection.InvalidDate)

        if (input.lines.isEmpty() || input.lines.size > ORDER_ITEMS_MAX) return reject(OrderRejection.NoItems)

        val known = products.getProducts()
        val seen = HashSet<String>()
        val items = ArrayList<OrderItem>()

        for (line in input.lines) {
            val unitPrice = cleanAmount(line.unitPrice)
            if (line.quantity < 1 || line.quantity.toDouble() > Limits.STOCK_MAX || unitPrice == null) {
                return reject(OrderRejection.InvalidItem)
            }
            val product = known.firstOrNull { it.id == line.productId }
                ?: return reject(OrderRejection.UnknownProduct)
            if (!seen.add(product.id)) return reject(OrderRejection.DuplicateProduct)
            items.add(OrderItem(product.id, product.name, line.quantity, unitPrice))
        }

        val total = items.sumOf { it.quantity * it.unitPrice }
        if (total > ModuleLimits.AMOUNT_MAX) return reject(OrderRejection.TooHigh)

        val order = Order(
            id = newId(),
            kind = input.kind,
            partyId = partyId,
            partyName = partyName,
            items = items,
            total = total,
            note = cleanLabel(input.note, ModuleLimits.TEXT_MAX),
            expectedDate = expectedDate,
            status = OrderStatus.Pending,
            createdAt = toIso(now())
        )

        return if (save(getOrders() + order)) AddOrderResult.Ok(order) else reject(OrderRejection.Storage)
    }

    private fun change(
        orderId: String,
        allowed: (Order) -> Boolean,
        update: (Order) -> Order
    ): OrderChangeResult {
        val orders = getOrders().toMutableList()
        val index = orders.indexOfFirst { it.id == orderId }
        if (index == -1) return OrderChangeResult.NotFound
        if (!allowed(orders[index])) return OrderChangeResult.NotAllowed
        orders[index] = update(orders[index])
        return if (save(orders)) OrderChangeResult.Ok else OrderChangeResult.StorageError
    }

    /** Commande client prête à être livrée (en attente → prête). */
    fun markOrderReady(orderId: String): OrderChangeResult = change(
        orderId,
        { it.kind == OrderKind.Customer && it.status == OrderStatus.Pending }
    ) { it.copy(status = OrderStatus.Ready) }

    /** Annule une commande en cours : aucun effet sur le stock ni la caisse. */
    fun cancelOrder(orderId: String): OrderChangeResult = change(
        orderId,
        { it.isOpen }
    ) { it.copy(status = OrderStatus.Cancelled, cancelledAt = toIso(now())) }

    /** Marque la commande terminée, avec la vente ou l'achat créé (utilisé à l'étape suivante). */
    fun markCompleted(orderId: String, linkId: String): Boolean = change(
        orderId,
        { true }
    ) {
        it.copy(status = OrderStatus.Completed, completedAt = toIso(now()), linkId = linkId)
    } == OrderChangeResult.Ok

    fun getOrdersSummary(
        at: Instant = now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): OrdersSummary {
        var customerOpen = 0
        var supplierOpen = 0
        var overdue = 0

        for (order in getOrders()) {
            if (!order.isOpen) continue
            if (order.kind == OrderKind.Customer) customerOpen += 1 else supplierOpen += 1
            if (isOrderOverdue(order, at, zone)) overdue += 1
        }
        return OrdersSummary(customerOpen, supplierOpen, overdue)
    }
}
