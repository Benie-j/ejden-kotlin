package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val ORD_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")
private val ORD_RIZ = Product("p1", "Riz", 500.0, 700.0, 10, 2, null, null, null)
private val ORD_HUILE = Product("p2", "Huile", 1000.0, 1400.0, 5, 1, null, null, null)
private val ORD_KONE = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")
private val ORD_AWA = Client("c1", "Awa Koné", "07 11", "2026-01-01T10:00:00.000Z")

private class OrderEnv {
    val store = InMemoryStore()
    var clock: Instant = ORD_NOW
    val products = ProductRepository(store)
    val suppliers = SupplierRepository(store)
    val sales = SaleRepository(store, products) { clock }
    val credits = CreditPaymentRepository(store, sales) { clock }
    val clients = ClientRepository(store, sales, credits)
    val orders = OrderRepository(store, suppliers, clients, products) { clock }

    init {
        products.addProduct(ORD_RIZ)
        products.addProduct(ORD_HUILE)
        suppliers.addSupplier(ORD_KONE)
        clients.addClient(ORD_AWA)
    }

    fun customerOrder(
        lines: List<OrderInputLine> = listOf(OrderInputLine("p1", 2, 700.0)),
        partyId: String? = null,
        note: String = "",
        date: String? = null
    ) = OrderInput(OrderKind.Customer, partyId, lines, note, date)

    fun supplierOrder(
        lines: List<OrderInputLine> = listOf(OrderInputLine("p1", 20, 450.0)),
        partyId: String? = "f1",
        date: String? = null
    ) = OrderInput(OrderKind.Supplier, partyId, lines, "", date)

    fun add(input: OrderInput): Order = (orders.addOrder(input) as AddOrderResult.Ok).order
}

private fun refused(reason: OrderRejection) = AddOrderResult.Rejected(reason)

class OrderTest {

    @Test
    fun commandeClientDePassage() {
        val e = OrderEnv()
        val order = e.add(
            e.customerOrder(
                listOf(OrderInputLine("p1", 2, 700.0), OrderInputLine("p2", 1, 1400.0)),
                note = "  Pour   samedi ",
                date = "2026-10-20"
            )
        )

        assertEquals(2800.0, order.total, 0.0)
        assertEquals(WALK_IN_CLIENT, order.partyName)
        assertNull(order.partyId)
        assertEquals(OrderStatus.Pending, order.status)
        assertEquals("Pour samedi", order.note)
        assertEquals("2026-10-20", order.expectedDate)
        assertEquals("2026-10-15T12:00:00.000Z", order.createdAt)
        assertEquals("Riz", order.items[0].productName)

        // Relue depuis le disque : identique, et sans effet sur le stock.
        assertEquals(listOf(order), e.orders.getOrders())
        assertEquals(10, e.products.getProductById("p1")!!.stock)
    }

    @Test
    fun commandeClientEnregistre() {
        val e = OrderEnv()
        val order = e.add(e.customerOrder(partyId = "c1"))
        assertEquals("c1", order.partyId)
        assertEquals("Awa Koné", order.partyName)

        assertEquals(refused(OrderRejection.PartyNotFound), e.orders.addOrder(e.customerOrder(partyId = "zzz")))
        // Une chaîne vide veut dire « client de passage ».
        assertEquals(WALK_IN_CLIENT, e.add(e.customerOrder(partyId = "")).partyName)
    }

    @Test
    fun commandeFournisseurExigeUnFournisseur() {
        val e = OrderEnv()
        assertEquals(refused(OrderRejection.PartyRequired), e.orders.addOrder(e.supplierOrder(partyId = null)))
        assertEquals(refused(OrderRejection.PartyNotFound), e.orders.addOrder(e.supplierOrder(partyId = "zzz")))

        val order = e.add(e.supplierOrder())
        assertEquals("Koné Distribution", order.partyName)
        assertEquals(OrderKind.Supplier, order.kind)
        assertEquals(9000.0, order.total, 0.0)
    }

    @Test
    fun refusDesCommandesInvalides() {
        val e = OrderEnv()
        val ok = OrderInputLine("p1", 2, 700.0)

        fun assertRefused(input: OrderInput, reason: OrderRejection) =
            assertEquals(refused(reason), e.orders.addOrder(input))

        assertRefused(e.customerOrder(lines = emptyList()), OrderRejection.NoItems)
        assertRefused(e.customerOrder(listOf(OrderInputLine("p1", 0, 700.0))), OrderRejection.InvalidItem)
        assertRefused(e.customerOrder(listOf(OrderInputLine("p1", 2, 0.0))), OrderRejection.InvalidItem)
        assertRefused(e.customerOrder(listOf(OrderInputLine("p1", 1_000_001, 700.0))), OrderRejection.InvalidItem)
        assertRefused(e.customerOrder(listOf(OrderInputLine("zzz", 2, 700.0))), OrderRejection.UnknownProduct)
        assertRefused(e.customerOrder(listOf(ok, ok)), OrderRejection.DuplicateProduct)
        assertRefused(e.customerOrder(listOf(OrderInputLine("p1", 1_000_000, 700.0))), OrderRejection.TooHigh)
        assertRefused(e.customerOrder(date = "2026-13-45"), OrderRejection.InvalidDate)

        assertTrue(e.orders.getOrders().isEmpty())
    }

    @Test
    fun pretePuisAnnulee() {
        val e = OrderEnv()
        val customer = e.add(e.customerOrder())
        val supplier = e.add(e.supplierOrder())

        assertEquals(OrderChangeResult.Ok, e.orders.markOrderReady(customer.id))
        assertEquals(OrderStatus.Ready, e.orders.getOrderById(customer.id)!!.status)
        assertEquals(OrderChangeResult.NotAllowed, e.orders.markOrderReady(customer.id))
        assertEquals(OrderChangeResult.NotAllowed, e.orders.markOrderReady(supplier.id))
        assertEquals(OrderChangeResult.NotFound, e.orders.markOrderReady("zzz"))

        assertEquals(OrderChangeResult.Ok, e.orders.cancelOrder(customer.id))
        val cancelled = e.orders.getOrderById(customer.id)!!
        assertEquals(OrderStatus.Cancelled, cancelled.status)
        assertNotNull(cancelled.cancelledAt)
        assertEquals(OrderChangeResult.NotAllowed, e.orders.cancelOrder(customer.id))
        assertEquals(OrderChangeResult.NotFound, e.orders.cancelOrder("zzz"))
    }

    @Test
    fun lectureRefuseLesCommandesIncoherentes() {
        val e = OrderEnv()
        val customer = e.add(e.customerOrder())
        val supplier = e.add(e.supplierOrder())

        assertNotNull(normalizeOrder(customer.toJson()))
        assertNotNull(normalizeOrder(customer.copy(status = OrderStatus.Ready).toJson()))
        assertNull(normalizeOrder(customer.toJson().put("total", 1.0)))
        assertNull(normalizeOrder(supplier.toJson().put("partyId", JSONObject.NULL)))
        assertNull(normalizeOrder(supplier.toJson().put("status", "ready")))
        assertNull(normalizeOrder(JSONObject().put("id", "x")))
    }

    @Test
    fun retardEtResume() {
        val e = OrderEnv()
        val late = e.add(e.customerOrder(date = "2026-10-14"))
        val today = e.add(e.customerOrder(date = "2026-10-15"))
        e.orders.markOrderReady(today.id)
        e.add(e.supplierOrder())
        val dropped = e.add(e.supplierOrder(date = "2026-10-01"))
        e.orders.cancelOrder(dropped.id)

        assertTrue(isOrderOverdue(late, ORD_NOW, ZoneOffset.UTC))
        // Le jour prévu, la commande n'est pas encore en retard.
        assertFalse(isOrderOverdue(today, ORD_NOW, ZoneOffset.UTC))
        assertFalse(isOrderOverdue(e.orders.getOrderById(dropped.id)!!, ORD_NOW, ZoneOffset.UTC))

        assertEquals(OrdersSummary(2, 1, 1), e.orders.getOrdersSummary(ORD_NOW, ZoneOffset.UTC))
    }
}
