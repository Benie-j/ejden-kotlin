package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val NOW_PUR: Instant = Instant.parse("2026-10-16T10:00:00Z")
private val RIZ_PUR = Product("p1", "Riz", 500.0, 700.0, 10, 2, null, null, null)
private val HUILE_PUR = Product("p2", "Huile", 1000.0, 1400.0, 5, 1, null, null, null)
private val FOURNISSEUR_PUR = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")

private class FailOnPurchases(private val inner: InMemoryStore) : KeyValueStore {
    override fun get(key: String): String? = inner.get(key)
    override fun set(key: String, value: String): Boolean =
        if (key == PurchaseRepository.KEY) false else inner.set(key, value)
    override fun remove(key: String) = inner.remove(key)
}

private class PurchaseSetup(store: KeyValueStore = InMemoryStore()) {
    val products = ProductRepository(store)
    val suppliers = SupplierRepository(store)
    val purchases = PurchaseRepository(store, suppliers, products) { NOW_PUR }

    init {
        products.addProduct(RIZ_PUR)
        products.addProduct(HUILE_PUR)
        suppliers.addSupplier(FOURNISSEUR_PUR)
    }

    fun stockOf(id: String): Int = products.getProductById(id)!!.stock
}

private fun order(
    lines: List<PurchaseLine>,
    paid: Double = 0.0,
    note: String = "",
    update: Boolean = false,
    supplierId: String = "f1"
) = PurchaseInput(supplierId, lines, paid, note, update)

private fun refusal(reason: PurchaseRejection) = RecordPurchaseResult.Rejected(reason)

class PurchaseTest {

    @Test
    fun unAchatAugmenteLeStock() {
        val s = PurchaseSetup()
        val result = s.purchases.recordPurchase(
            order(
                listOf(PurchaseLine("p1", 20, 450.0), PurchaseLine("p2", 5, 1100.0)),
                paid = 5000.0,
                note = "  Livraison   lundi "
            )
        ) as RecordPurchaseResult.Ok

        val purchase = result.purchase
        assertEquals(14500.0, purchase.total, 0.0)
        assertEquals(5000.0, purchase.paid, 0.0)
        assertEquals("Koné Distribution", purchase.supplierName)
        assertEquals("Livraison lundi", purchase.note)
        assertEquals("2026-10-16T10:00:00.000Z", purchase.createdAt)
        assertFalse(purchase.isCancelled)

        assertEquals(30, s.stockOf("p1"))
        assertEquals(10, s.stockOf("p2"))
        // Sans « mettre à jour les prix », le prix d'achat du produit ne bouge pas.
        assertEquals(500.0, s.products.getProductById("p1")!!.purchasePrice, 0.0)

        // Relu depuis le disque : identique.
        assertEquals(listOf(purchase), s.purchases.getPurchases())
    }

    @Test
    fun miseAJourDesPrix() {
        val s = PurchaseSetup()
        s.purchases.recordPurchase(order(listOf(PurchaseLine("p1", 1, 450.0)), update = true))
        assertEquals(450.0, s.products.getProductById("p1")!!.purchasePrice, 0.0)
        assertEquals(700.0, s.products.getProductById("p1")!!.salePrice, 0.0)
    }

    @Test
    fun refusDesAchatsInvalides() {
        val s = PurchaseSetup()
        val ok = PurchaseLine("p1", 2, 450.0)

        fun check(input: PurchaseInput, reason: PurchaseRejection) =
            assertEquals(refusal(reason), s.purchases.recordPurchase(input))

        check(order(listOf(ok), supplierId = "inconnu"), PurchaseRejection.SupplierNotFound)
        check(order(emptyList()), PurchaseRejection.NoItems)
        check(order(listOf(PurchaseLine("p1", 0, 450.0))), PurchaseRejection.InvalidItem)
        check(order(listOf(PurchaseLine("p1", 2, 0.0))), PurchaseRejection.InvalidItem)
        check(order(listOf(PurchaseLine("zzz", 2, 450.0))), PurchaseRejection.UnknownProduct)
        check(order(listOf(ok, ok)), PurchaseRejection.DuplicateProduct)
        check(order(listOf(PurchaseLine("p1", 1_000_000, 450.0))), PurchaseRejection.TooHigh)
        check(order(listOf(ok), paid = 901.0), PurchaseRejection.InvalidPaid)
        check(order(listOf(ok), paid = -1.0), PurchaseRejection.InvalidPaid)
        check(order(listOf(ok), paid = 10.5), PurchaseRejection.InvalidPaid)

        // Rien n'a bougé.
        assertEquals(10, s.stockOf("p1"))
        assertTrue(s.purchases.getPurchases().isEmpty())

        // Payer exactement le total est permis.
        assertTrue(s.purchases.recordPurchase(order(listOf(ok), paid = 900.0)) is RecordPurchaseResult.Ok)
    }

    @Test
    fun echecDEnregistrementRemetLeStock() {
        val s = PurchaseSetup(FailOnPurchases(InMemoryStore()))
        val result = s.purchases.recordPurchase(order(listOf(PurchaseLine("p1", 5, 450.0))))
        assertEquals(refusal(PurchaseRejection.Storage), result)
        assertEquals(10, s.stockOf("p1"))
    }

    @Test
    fun lectureRefuseLesAchatsIncoherents() {
        val s = PurchaseSetup()
        val purchase = (s.purchases.recordPurchase(
            order(listOf(PurchaseLine("p1", 2, 450.0)), paid = 100.0)
        ) as RecordPurchaseResult.Ok).purchase

        assertNotNull(normalizePurchase(purchase.toJson()))
        assertNull(normalizePurchase(purchase.toJson().put("total", 1.0)))
        assertNull(normalizePurchase(purchase.toJson().put("paid", 99999.0)))
        assertNull(normalizePurchase(JSONObject().put("id", "x")))

        val cancelled = purchase.copy(cancelledAt = "2026-10-17T08:00:00.000Z")
        assertTrue(normalizePurchase(cancelled.toJson())!!.isCancelled)
    }
}
