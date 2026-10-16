package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val DEBT_T1: Instant = Instant.parse("2026-10-10T09:00:00Z")
private val DEBT_T2: Instant = Instant.parse("2026-10-12T09:00:00Z")
private val DEBT_T3: Instant = Instant.parse("2026-10-15T09:00:00Z")
private val DEBT_RIZ = Product("p1", "Riz", 500.0, 700.0, 10, 2, null, null, null)
private val DEBT_HUILE = Product("p2", "Huile", 1000.0, 1400.0, 5, 1, null, null, null)
private val DEBT_KONE = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")

private class DebtSetup {
    val store = InMemoryStore()
    var clock: Instant = DEBT_T1
    val products = ProductRepository(store)
    val suppliers = SupplierRepository(store)
    val purchases = PurchaseRepository(store, suppliers, products) { clock }
    val payments = SupplierPaymentRepository(store, purchases) { clock }

    init {
        products.addProduct(DEBT_RIZ)
        products.addProduct(DEBT_HUILE)
        suppliers.addSupplier(DEBT_KONE)
    }

    /** Achat A : 2 x 450 = 900, rien payé. Achat B : 1 x 1100, 100 payés (1000 dus). */
    fun twoPurchases(): Pair<Purchase, Purchase> {
        clock = DEBT_T1
        val a = purchases.recordPurchase(
            PurchaseInput("f1", listOf(PurchaseLine("p1", 2, 450.0)), 0.0, "", false)
        ) as RecordPurchaseResult.Ok
        clock = DEBT_T2
        val b = purchases.recordPurchase(
            PurchaseInput("f1", listOf(PurchaseLine("p2", 1, 1100.0)), 100.0, "", false)
        ) as RecordPurchaseResult.Ok
        clock = DEBT_T3
        return Pair(a.purchase, b.purchase)
    }

    fun stockOf(id: String): Int = products.getProductById(id)!!.stock
}

class SupplierPaymentTest {

    @Test
    fun detteParFournisseur() {
        val s = DebtSetup()
        val (a, b) = s.twoPurchases()

        val debt = s.payments.getSupplierDebt("f1")
        assertEquals(1900.0, debt.amount, 0.0)
        assertEquals(listOf(a.id, b.id), debt.purchases.map { it.purchase.id })
        assertEquals(listOf(900.0, 1000.0), debt.purchases.map { it.remaining })
        assertEquals(1900.0, s.payments.getSuppliersDebtTotal(), 0.0)
        assertEquals(0.0, s.payments.getSupplierDebt("autre").amount, 0.0)
    }

    @Test
    fun paiementRepartiSurLesAchatsLesPlusAnciens() {
        val s = DebtSetup()
        val (a, b) = s.twoPurchases()

        assertEquals(SupplierPaymentResult.Ok, s.payments.recordSupplierPayment("f1", 1200.0, PaymentMethod.Cash))

        val debt = s.payments.getSupplierDebt("f1")
        assertEquals(700.0, debt.amount, 0.0)
        assertEquals(listOf(b.id), debt.purchases.map { it.purchase.id })

        val all = s.payments.getSupplierPayments()
        assertEquals(listOf(900.0, 300.0), all.map { it.amount })
        assertEquals(listOf(a.id, b.id), all.map { it.purchaseId })
        assertEquals(1, all.map { it.batchId }.distinct().size)

        val batches = s.payments.getSupplierPaymentBatches("f1")
        assertEquals(1, batches.size)
        assertEquals(1200.0, batches[0].amount, 0.0)
        assertEquals(PaymentMethod.Cash, batches[0].method)
    }

    @Test
    fun refusDePaiement() {
        val s = DebtSetup()
        s.twoPurchases()

        assertEquals(SupplierPaymentResult.InvalidAmount, s.payments.recordSupplierPayment("f1", 0.0, PaymentMethod.Cash))
        assertEquals(SupplierPaymentResult.InvalidMethod, s.payments.recordSupplierPayment("f1", 100.0, PaymentMethod.Credit))
        assertEquals(SupplierPaymentResult.TooMuch(1900.0), s.payments.recordSupplierPayment("f1", 2000.0, PaymentMethod.Cash))
        assertEquals(SupplierPaymentResult.NoDebt, s.payments.recordSupplierPayment("autre", 100.0, PaymentMethod.Cash))
        assertTrue(s.payments.getSupplierPayments().isEmpty())
    }

    @Test
    fun annulationDUnVersement() {
        val s = DebtSetup()
        s.twoPurchases()
        s.payments.recordSupplierPayment("f1", 1200.0, PaymentMethod.MobileMoney)
        val batchId = s.payments.getSupplierPaymentBatches("f1")[0].batchId

        assertEquals(CancelSupplierPaymentResult.Ok, s.payments.cancelSupplierPayment(batchId))
        assertEquals(1900.0, s.payments.getSupplierDebt("f1").amount, 0.0)
        assertNotNull(s.payments.getSupplierPaymentBatches("f1")[0].cancelledAt)

        assertEquals(CancelSupplierPaymentResult.AlreadyCancelled, s.payments.cancelSupplierPayment(batchId))
        assertEquals(CancelSupplierPaymentResult.NotFound, s.payments.cancelSupplierPayment("zzz"))
    }

    @Test
    fun annulationDUnAchat() {
        val s = DebtSetup()
        val (a, b) = s.twoPurchases()
        assertEquals(12, s.stockOf("p1"))

        assertEquals(CancelPurchaseResult.Ok(0), s.purchases.cancelPurchase(a.id))
        assertEquals(10, s.stockOf("p1"))
        assertTrue(s.purchases.getPurchases().first { it.id == a.id }.isCancelled)
        assertFalse(s.purchases.getPurchases().first { it.id == b.id }.isCancelled)

        // Un achat annulé n'est plus une dette.
        assertEquals(1000.0, s.payments.getSupplierDebt("f1").amount, 0.0)

        assertEquals(CancelPurchaseResult.AlreadyCancelled, s.purchases.cancelPurchase(a.id))
        assertEquals(CancelPurchaseResult.NotFound, s.purchases.cancelPurchase("zzz"))
    }

    @Test
    fun annulationRefuseeTantQuIlYADesPaiements() {
        val s = DebtSetup()
        val (a, _) = s.twoPurchases()
        s.payments.recordSupplierPayment("f1", 100.0, PaymentMethod.Cash)

        assertEquals(CancelPurchaseResult.HasPayments, s.purchases.cancelPurchase(a.id))
        assertEquals(12, s.stockOf("p1"))

        val batchId = s.payments.getSupplierPaymentBatches("f1")[0].batchId
        s.payments.cancelSupplierPayment(batchId)
        assertEquals(CancelPurchaseResult.Ok(0), s.purchases.cancelPurchase(a.id))
    }

    @Test
    fun annulationRefuseeSiLeStockEstDejaUtilise() {
        val s = DebtSetup()
        val (a, _) = s.twoPurchases()
        s.products.updateProduct(s.products.getProductById("p1")!!.copy(stock = 1))

        assertEquals(CancelPurchaseResult.StockUsed("Riz"), s.purchases.cancelPurchase(a.id))
        assertEquals(1, s.stockOf("p1"))
        assertFalse(s.purchases.getPurchases().first { it.id == a.id }.isCancelled)
    }

    @Test
    fun annulationQuandLeProduitAEteSupprime() {
        val s = DebtSetup()
        val (a, _) = s.twoPurchases()
        s.products.deleteProduct("p1")

        assertEquals(CancelPurchaseResult.Ok(1), s.purchases.cancelPurchase(a.id))
        assertTrue(s.purchases.getPurchases().first { it.id == a.id }.isCancelled)
    }

    @Test
    fun suppressionDUneFiche() {
        val s = DebtSetup()
        s.twoPurchases()

        assertEquals(DeleteSupplierResult.NotFound, s.suppliers.deleteSupplier("zzz"))
        // On lui doit de l'argent : refusé.
        assertEquals(DeleteSupplierResult.HasDebt, s.suppliers.deleteSupplier("f1"))
        assertEquals(1, s.suppliers.getSuppliers().size)

        s.payments.recordSupplierPayment("f1", 1900.0, PaymentMethod.Cash)
        assertEquals(DeleteSupplierResult.Ok, s.suppliers.deleteSupplier("f1"))
        assertTrue(s.suppliers.getSuppliers().isEmpty())

        // L'historique des achats reste lisible.
        val history = s.purchases.getPurchases()
        assertEquals(2, history.size)
        assertEquals("Koné Distribution", history[0].supplierName)
    }
}
