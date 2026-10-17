package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val CMP_NOW: Instant = Instant.parse("2026-10-15T12:00:00Z")
private val CMP_RIZ = Product("p1", "Riz", 500.0, 700.0, 10, 2, null, null, null)
private val CMP_KONE = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")
private val CMP_AWA = Client("c1", "Awa Koné", "07 11", "2026-01-01T10:00:00.000Z")

private class FailingOrdersStore(private val inner: InMemoryStore) : KeyValueStore {
    var failOrders = false
    override fun get(key: String): String? = inner.get(key)
    override fun set(key: String, value: String): Boolean =
        if (failOrders && key == OrderRepository.KEY) false else inner.set(key, value)
    override fun remove(key: String) = inner.remove(key)
}

private class CompletionEnv {
    val store = FailingOrdersStore(InMemoryStore())
    var clock: Instant = CMP_NOW
    val products = ProductRepository(store)
    val suppliers = SupplierRepository(store)
    val sales = SaleRepository(store, products) { clock }
    val credits = CreditPaymentRepository(store, sales) { clock }
    val clients = ClientRepository(store, sales, credits)
    val purchases = PurchaseRepository(store, suppliers, products) { clock }
    val orders = OrderRepository(store, suppliers, clients, products) { clock }
    val completion = OrderCompletion(orders, sales, purchases, products) { clock }

    init {
        products.addProduct(CMP_RIZ)
        suppliers.addSupplier(CMP_KONE)
        clients.addClient(CMP_AWA)
    }

    /** Commande client : quantité x 700 FCFA de riz. */
    fun customerOrder(partyId: String? = null, quantity: Int = 2): Order =
        (orders.addOrder(
            OrderInput(OrderKind.Customer, partyId, listOf(OrderInputLine("p1", quantity, 700.0)), "", null)
        ) as AddOrderResult.Ok).order

    /** Commande fournisseur : 20 x 450 FCFA de riz = 9000. */
    fun supplierOrder(): Order =
        (orders.addOrder(
            OrderInput(OrderKind.Supplier, "f1", listOf(OrderInputLine("p1", 20, 450.0)), "", null)
        ) as AddOrderResult.Ok).order

    fun stockOf(id: String): Int = products.getProductById(id)!!.stock
}

class OrderCompletionTest {

    @Test
    fun livraisonPayeeEnTotalite() {
        val e = CompletionEnv()
        val order = e.customerOrder()

        val result = e.completion.completeCustomerOrder(order.id, PaymentMethod.Cash, 1400.0) as CompleteOrderResult.Ok

        val sale = e.sales.getSales().single()
        assertEquals(sale.id, result.linkId)
        assertEquals(1400.0, sale.total, 0.0)
        assertEquals(PaymentMethod.Cash, sale.paymentMethod)
        assertEquals(0.0, sale.remaining, 0.0)
        assertNull(sale.customerId)
        assertNull(sale.borrowerName)
        assertEquals(500.0, sale.items[0].unitCost!!, 0.0)
        assertEquals(2.0, sale.items[0].quantity, 0.0)
        assertEquals(8, e.stockOf("p1"))

        val done = e.orders.getOrderById(order.id)!!
        assertEquals(OrderStatus.Completed, done.status)
        assertEquals(sale.id, done.linkId)
        assertNotNull(done.completedAt)
    }

    @Test
    fun livraisonAvecCredit() {
        val e = CompletionEnv()
        val order = e.customerOrder(partyId = "c1")

        e.completion.completeCustomerOrder(order.id, PaymentMethod.MobileMoney, 400.0) as CompleteOrderResult.Ok

        val sale = e.sales.getSales().single()
        assertEquals(PaymentMethod.Credit, sale.paymentMethod)
        assertEquals(400.0, sale.amountPaid, 0.0)
        assertEquals(1000.0, sale.remaining, 0.0)
        assertEquals("c1", sale.customerId)
        assertEquals("Awa Koné", sale.borrowerName)
    }

    @Test
    fun refusDeLivraison() {
        val e = CompletionEnv()
        val walkIn = e.customerOrder()

        assertEquals(CompleteOrderResult.InvalidMethod, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Credit, 1400.0))
        assertEquals(CompleteOrderResult.InvalidAmount, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, 1500.0))
        assertEquals(CompleteOrderResult.InvalidAmount, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, -1.0))
        assertEquals(CompleteOrderResult.InvalidAmount, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, 10.5))
        // Un client de passage ne peut pas avoir de crédit.
        assertEquals(CompleteOrderResult.CreditNeedsClient, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, 400.0))
        assertEquals(CompleteOrderResult.NotFound, e.completion.completeCustomerOrder("zzz", PaymentMethod.Cash, 0.0))
        assertEquals(CompleteOrderResult.NotAllowed, e.completion.completeCustomerOrder(e.supplierOrder().id, PaymentMethod.Cash, 100.0))

        val tooBig = e.customerOrder(quantity = 11)
        assertEquals(CompleteOrderResult.OutOfStock("Riz"), e.completion.completeCustomerOrder(tooBig.id, PaymentMethod.Cash, 7700.0))

        // Rien n'a bougé.
        assertEquals(10, e.stockOf("p1"))
        assertTrue(e.sales.getSales().isEmpty())
        assertEquals(OrderStatus.Pending, e.orders.getOrderById(tooBig.id)!!.status)

        // Une commande terminée ne peut plus être livrée.
        assertTrue(e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, 1400.0) is CompleteOrderResult.Ok)
        assertEquals(CompleteOrderResult.NotAllowed, e.completion.completeCustomerOrder(walkIn.id, PaymentMethod.Cash, 1400.0))
    }

    @Test
    fun receptionDUneCommandeFournisseur() {
        val e = CompletionEnv()
        val order = e.supplierOrder()

        val result = e.completion.receiveSupplierOrder(order.id, 4000.0, true) as CompleteOrderResult.Ok

        val purchase = e.purchases.getPurchases().single()
        assertEquals(purchase.id, result.linkId)
        assertEquals(9000.0, purchase.total, 0.0)
        assertEquals(4000.0, purchase.paid, 0.0)
        assertEquals("Commande reçue", purchase.note)
        assertEquals("Koné Distribution", purchase.supplierName)
        assertEquals(30, e.stockOf("p1"))
        assertEquals(450.0, e.products.getProductById("p1")!!.purchasePrice, 0.0)

        val done = e.orders.getOrderById(order.id)!!
        assertEquals(OrderStatus.Completed, done.status)
        assertEquals(purchase.id, done.linkId)
    }

    @Test
    fun refusDeReception() {
        val e = CompletionEnv()
        val order = e.supplierOrder()

        assertEquals(
            CompleteOrderResult.PurchaseRejected(PurchaseRejection.InvalidPaid),
            e.completion.receiveSupplierOrder(order.id, 9500.0, false)
        )
        assertEquals(OrderStatus.Pending, e.orders.getOrderById(order.id)!!.status)
        assertEquals(10, e.stockOf("p1"))
        assertTrue(e.purchases.getPurchases().isEmpty())

        assertEquals(CompleteOrderResult.NotAllowed, e.completion.receiveSupplierOrder(e.customerOrder().id, 0.0, false))
        assertEquals(CompleteOrderResult.NotFound, e.completion.receiveSupplierOrder("zzz", 0.0, false))
    }

    @Test
    fun echecDEnregistrementAnnuleLaVente() {
        val e = CompletionEnv()
        val order = e.customerOrder()
        e.store.failOrders = true

        assertEquals(CompleteOrderResult.StorageError, e.completion.completeCustomerOrder(order.id, PaymentMethod.Cash, 1400.0))

        assertEquals(10, e.stockOf("p1"))
        assertTrue(e.sales.getSales().single().isCancelled)
        e.store.failOrders = false
        assertEquals(OrderStatus.Pending, e.orders.getOrderById(order.id)!!.status)
    }

    @Test
    fun echecDEnregistrementAnnuleLAchat() {
        val e = CompletionEnv()
        val order = e.supplierOrder()
        e.store.failOrders = true

        assertEquals(CompleteOrderResult.StorageError, e.completion.receiveSupplierOrder(order.id, 0.0, false))

        assertEquals(10, e.stockOf("p1"))
        assertTrue(e.purchases.getPurchases().single().isCancelled)
        e.store.failOrders = false
        assertEquals(OrderStatus.Pending, e.orders.getOrderById(order.id)!!.status)
    }
}
