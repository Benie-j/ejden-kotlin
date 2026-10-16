package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val LG_T1: Instant = Instant.parse("2026-10-10T09:00:00Z")
private val LG_T2: Instant = Instant.parse("2026-10-12T09:00:00Z")
private val LG_T3: Instant = Instant.parse("2026-10-15T09:00:00Z")
private val LG_RIZ = Product("p1", "Riz", 500.0, 700.0, 10, 2, null, null, null)
private val LG_HUILE = Product("p2", "Huile", 1000.0, 1400.0, 5, 1, null, null, null)
private val LG_KONE = Supplier("f1", "Koné Distribution", "07 00", "2026-01-01T10:00:00.000Z")

private class LedgerEnv {
    val store = InMemoryStore()
    var clock: Instant = LG_T1
    val products = ProductRepository(store)
    val suppliers = SupplierRepository(store)
    val purchases = PurchaseRepository(store, suppliers, products) { clock }
    val payments = SupplierPaymentRepository(store, purchases) { clock }
    val sales = SaleRepository(store, products) { clock }
    val credits = CreditPaymentRepository(store, sales) { clock }
    val expenses = ExpenseRepository(store)
    val cash = CashRepository(
        store, sales, credits, expenses,
        extraMovements = { purchaseCashMovements(purchases.getPurchases(), payments.getSupplierPayments()) }
    )
    val stock = StockRepository(
        store, products, sales,
        extraHistory = { id -> purchaseStockHistory(purchases.getPurchases(), id) }
    )

    init {
        products.addProduct(LG_RIZ)
        products.addProduct(LG_HUILE)
        suppliers.addSupplier(LG_KONE)
    }

    /** Achat A : 2 x 450 = 900, rien payé. Achat B : 1 x 1100, 100 payés. */
    fun twoPurchases(): Pair<Purchase, Purchase> {
        clock = LG_T1
        val a = purchases.recordPurchase(
            PurchaseInput("f1", listOf(PurchaseLine("p1", 2, 450.0)), 0.0, "", false)
        ) as RecordPurchaseResult.Ok
        clock = LG_T2
        val b = purchases.recordPurchase(
            PurchaseInput("f1", listOf(PurchaseLine("p2", 1, 1100.0)), 100.0, "", false)
        ) as RecordPurchaseResult.Ok
        clock = LG_T3
        return Pair(a.purchase, b.purchase)
    }
}

class PurchaseLedgerTest {

    @Test
    fun laCaisseSuitLesAchatsEtLesVersements() {
        val e = LedgerEnv()
        e.twoPurchases()
        e.payments.recordSupplierPayment("f1", 1200.0, PaymentMethod.Cash)

        val summary = e.cash.getCashSummary(LG_T3, ZoneOffset.UTC)
        assertEquals(-1300.0, summary.balance, 0.0)
        assertEquals(0.0, summary.todayIn, 0.0)
        assertEquals(1200.0, summary.todayOut, 0.0)
        assertEquals(
            listOf(CashSource.SupplierPayment, CashSource.SupplierPayment, CashSource.Purchase),
            summary.movements.map { it.source }
        )
        assertEquals("Paiement fournisseur · Koné Distribution", summary.movements.first().label)
        assertEquals("Achat · Koné Distribution", summary.movements.last().label)
        assertEquals(-100.0, summary.movements.last().amount, 0.0)
        // Les ventes par mode de paiement ne sont pas touchées.
        assertTrue(summary.salesByMethod.values.all { it == 0.0 })
    }

    @Test
    fun lesAnnulationsCorrigentLaCaisse() {
        val e = LedgerEnv()
        val (_, b) = e.twoPurchases()
        e.payments.recordSupplierPayment("f1", 1200.0, PaymentMethod.Cash)
        val batchId = e.payments.getSupplierPaymentBatches("f1")[0].batchId

        // Versement annulé : il disparaît de la caisse, il reste le payé à la livraison.
        e.payments.cancelSupplierPayment(batchId)
        assertEquals(-100.0, e.cash.getCashSummary(LG_T3, ZoneOffset.UTC).balance, 0.0)

        // Achat annulé : sa sortie de caisse disparaît aussi.
        assertEquals(CancelPurchaseResult.Ok(0), e.purchases.cancelPurchase(b.id))
        val after = e.cash.getCashSummary(LG_T3, ZoneOffset.UTC)
        assertEquals(0.0, after.balance, 0.0)
        assertTrue(after.movements.isEmpty())
    }

    @Test
    fun lHistoriqueDuStockContientLesAchats() {
        val e = LedgerEnv()
        val (a, _) = e.twoPurchases()

        var history = e.stock.getHistory("p1")
        assertEquals(1, history.size)
        assertEquals("Achat · Koné Distribution", history[0].label)
        assertEquals(2.0, history[0].delta, 0.0)
        assertEquals(StockSource.Purchase, history[0].source)

        e.purchases.cancelPurchase(a.id)
        history = e.stock.getHistory("p1")
        assertEquals(listOf(StockSource.PurchaseCancel, StockSource.Purchase), history.map { it.source })
        assertEquals(listOf(-2.0, 2.0), history.map { it.delta })
        assertEquals("Annulation achat · Koné Distribution", history[0].label)

        assertEquals(1, e.stock.getHistory("p2").size)
        assertEquals(3, e.stock.getHistory().size)
    }
}
