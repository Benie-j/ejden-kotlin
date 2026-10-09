package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val FIXED: Instant = Instant.parse("2026-10-09T10:00:00Z")

private class FailingStore(private val inner: KeyValueStore, private val failKey: String) : KeyValueStore {
    override fun get(key: String) = inner.get(key)
    override fun set(key: String, value: String) = if (key == failKey) false else inner.set(key, value)
    override fun remove(key: String) = inner.remove(key)
}

private class Env(val store: KeyValueStore = InMemoryStore()) {
    val products = ProductRepository(store)
    val sales = SaleRepository(store, products) { FIXED }
    val credits = CreditPaymentRepository(store, sales) { FIXED }
}

private fun product(id: String, stock: Int) =
    Product(id, "Prod $id", 100.0, 150.0, stock, 2, null, null, null)

private fun item(pid: String, qty: Double, cost: Double? = 100.0) =
    SaleItem(pid, "Prod $pid", 150.0, cost, qty, 150.0 * qty)

private fun sale(
    id: String,
    items: List<SaleItem>,
    remaining: Double = 0.0,
    createdAt: String = "2026-10-01T10:00:00.000Z",
    discount: Double = 0.0
): Sale {
    val subtotal = items.sumOf { it.total }
    val total = subtotal - discount
    return Sale(
        id, createdAt, items, subtotal, discount, total,
        if (remaining > 0.0) PaymentMethod.Credit else PaymentMethod.Cash,
        total - remaining, 0.0, remaining, null, null
    )
}

class SaleRepositoryTest {

    @Test
    fun venteAncienneSansPrixDAchatEstLue() {
        val store = InMemoryStore()
        store.set(
            "ejden_sales",
            """[{"id":"s1","createdAt":"2026-10-01T10:00:00.000Z",
            "items":[{"productId":"p1","productName":"Savon","unitPrice":750,"quantity":2,"total":1500}],
            "subtotal":1500,"discount":0,"total":1500,"paymentMethod":"cash",
            "amountPaid":2000,"change":500,"remaining":0,"customerId":null,"borrowerName":null}]"""
        )
        val s = Env(store).sales.getSales().single()
        assertNull(s.items[0].unitCost)
        assertNull(s.customerId)
        assertNull(s.cancelledAt)
        assertEquals(1500.0, s.total, 0.0)
    }

    @Test
    fun venteInvalideEcarteeAvecCopieDeSecurite() {
        val store = InMemoryStore()
        val raw = """[{"id":"s1","createdAt":"x","items":[],"subtotal":1,"discount":0,"total":1,
            "paymentMethod":"cash","amountPaid":1,"change":0,"customerId":null,"borrowerName":null}]"""
        store.set("ejden_sales", raw)
        assertTrue(Env(store).sales.getSales().isEmpty())
        assertEquals(raw, store.get("ejden_sales_backup"))
    }

    @Test
    fun venteBaisseLeStock() {
        val e = Env()
        e.products.addProduct(product("p1", 10))
        assertEquals(RecordSaleResult.Ok, e.sales.recordSale(sale("s1", listOf(item("p1", 3.0)))))
        assertEquals(7, e.products.getProductById("p1")!!.stock)
        assertEquals(1, e.sales.getSales().size)
    }

    @Test
    fun stockInsuffisantOuProduitInconnuRefuses() {
        val e = Env()
        e.products.addProduct(product("p1", 2))
        assertEquals(
            RecordSaleResult.OutOfStock("Prod p1"),
            e.sales.recordSale(sale("s1", listOf(item("p1", 3.0))))
        )
        assertEquals(
            RecordSaleResult.OutOfStock("Prod zz"),
            e.sales.recordSale(sale("s2", listOf(item("zz", 1.0))))
        )
        assertEquals(2, e.products.getProductById("p1")!!.stock)
        assertTrue(e.sales.getSales().isEmpty())
    }

    @Test
    fun echecDEcritureDeLaVenteRestaureLeStock() {
        val e = Env(FailingStore(InMemoryStore(), SaleRepository.KEY))
        e.products.addProduct(product("p1", 10))
        assertEquals(RecordSaleResult.StorageError, e.sales.recordSale(sale("s1", listOf(item("p1", 3.0)))))
        assertEquals(10, e.products.getProductById("p1")!!.stock)
    }

    @Test
    fun annulationRemetLeStockEtMarqueLaVente() {
        val e = Env()
        e.products.addProduct(product("p1", 10))
        e.sales.recordSale(sale("s1", listOf(item("p1", 3.0))))

        assertEquals(CancelSaleResult.Ok(0), e.sales.cancelSale("s1"))
        assertEquals(10, e.products.getProductById("p1")!!.stock)
        assertEquals("2026-10-09T10:00:00.000Z", e.sales.getSales().single().cancelledAt)

        assertEquals(CancelSaleResult.AlreadyCancelled, e.sales.cancelSale("s1"))
        assertEquals(CancelSaleResult.NotFound, e.sales.cancelSale("inconnue"))
    }

    @Test
    fun annulationPlafonneLeStockEtCompteLesProduitsSupprimes() {
        val e = Env()
        e.products.addProduct(product("p1", 10))
        e.products.addProduct(product("p2", 10))
        e.sales.recordSale(sale("s1", listOf(item("p1", 5.0), item("p2", 1.0))))

        val p1 = e.products.getProductById("p1")!!
        e.products.updateProduct(p1.copy(stock = 999_999))
        e.products.deleteProduct("p2")

        assertEquals(CancelSaleResult.Ok(1), e.sales.cancelSale("s1"))
        assertEquals(1_000_000, e.products.getProductById("p1")!!.stock)
    }

    @Test
    fun venteAvecPaiementActifNePeutPasEtreAnnulee() {
        val e = Env()
        e.products.addProduct(product("p1", 10))
        e.sales.recordSale(sale("s1", listOf(item("p1", 2.0)), remaining = 1000.0))
        e.credits.recordCreditPayment(listOf("s1"), 400.0, PaymentMethod.Cash)

        assertEquals(CancelSaleResult.HasPayments, e.sales.cancelSale("s1"))

        val paymentId = e.credits.getCreditPayments().single().id
        e.credits.cancelCreditPayment(paymentId)
        assertEquals(CancelSaleResult.Ok(0), e.sales.cancelSale("s1"))
    }

    @Test
    fun resteAPayerEstArrondiEtZeroSiAnnulee() {
        val s1 = sale("s1", listOf(item("p1", 1.0)), remaining = 1000.4)
        val s2 = sale("s2", listOf(item("p1", 1.0)), remaining = 500.0).copy(cancelledAt = "2026-10-02T10:00:00.000Z")
        val map = computeRemainingMap(listOf(s1, s2), emptyList())
        assertEquals(1000.0, map["s1"]!!, 0.0)
        assertEquals(0.0, map["s2"]!!, 0.0)
    }

    @Test
    fun remboursementRepartiDabordSurLaPlusAncienne() {
        val e = Env()
        e.sales.saveSales(
            listOf(
                sale("s1", listOf(item("p1", 1.0)), remaining = 1000.0),
                sale("s2", listOf(item("p1", 1.0)), remaining = 2000.0)
            )
        )
        val r = e.credits.recordCreditPayment(listOf("s1", "s2"), 1500.0, PaymentMethod.MobileMoney)
        assertEquals(
            CreditPaymentResult.Ok(listOf(AppliedPayment("s1", 1000.0), AppliedPayment("s2", 500.0))),
            r
        )
        val remaining = e.credits.getRemainingMap()
        assertEquals(0.0, remaining["s1"]!!, 0.0)
        assertEquals(1500.0, remaining["s2"]!!, 0.0)
    }

    @Test
    fun remboursementRefusesEtArrondis() {
        val e = Env()
        e.sales.saveSales(listOf(sale("s1", listOf(item("p1", 1.0)), remaining = 3000.0)))

        assertEquals(CreditPaymentResult.InvalidAmount, e.credits.recordCreditPayment(listOf("s1"), 0.0, PaymentMethod.Cash))
        assertEquals(CreditPaymentResult.InvalidAmount, e.credits.recordCreditPayment(listOf("s1"), -5.0, PaymentMethod.Cash))
        assertEquals(CreditPaymentResult.InvalidAmount, e.credits.recordCreditPayment(listOf("s1"), Double.NaN, PaymentMethod.Cash))
        assertEquals(CreditPaymentResult.InvalidMethod, e.credits.recordCreditPayment(listOf("s1"), 100.0, PaymentMethod.Credit))
        assertEquals(CreditPaymentResult.NoDebt, e.credits.recordCreditPayment(listOf("inconnue"), 100.0, PaymentMethod.Cash))
        assertEquals(CreditPaymentResult.TooMuch(3000.0), e.credits.recordCreditPayment(listOf("s1"), 3001.0, PaymentMethod.Cash))

        val ok = e.credits.recordCreditPayment(listOf("s1"), 1000.4, PaymentMethod.Cash)
        assertEquals(CreditPaymentResult.Ok(listOf(AppliedPayment("s1", 1000.0))), ok)
    }

    @Test
    fun annulationDUnPaiementRestitueLeReste() {
        val e = Env()
        e.sales.saveSales(listOf(sale("s1", listOf(item("p1", 1.0)), remaining = 1000.0)))
        e.credits.recordCreditPayment(listOf("s1"), 1000.0, PaymentMethod.Cash)
        val id = e.credits.getCreditPayments().single().id

        assertEquals(0.0, e.credits.getRemainingMap()["s1"]!!, 0.0)
        assertEquals(CancelCreditPaymentResult.Ok, e.credits.cancelCreditPayment(id))
        assertEquals(1000.0, e.credits.getRemainingMap()["s1"]!!, 0.0)
        assertEquals(CancelCreditPaymentResult.AlreadyCancelled, e.credits.cancelCreditPayment(id))
        assertEquals(CancelCreditPaymentResult.NotFound, e.credits.cancelCreditPayment("x"))
        assertNotNull(e.credits.getSalePayments("s1").single().cancelledAt)
    }

    @Test
    fun resumeDesVentesMargeEtPeriode() {
        val e = Env()
        e.sales.saveSales(
            listOf(
                sale("s1", listOf(item("p1", 2.0)), createdAt = "2026-10-05T10:00:00.000Z"),
                sale("s2", listOf(item("p1", 1.0, cost = null)), createdAt = "2026-10-06T10:00:00.000Z"),
                sale("s3", listOf(item("p1", 1.0)), createdAt = "2026-10-07T10:00:00.000Z")
                    .copy(cancelledAt = "2026-10-07T11:00:00.000Z"),
                sale("s4", listOf(item("p1", 1.0)), createdAt = "2026-11-02T10:00:00.000Z")
            )
        )
        val from = parseDateMillis("2026-10-01T00:00:00.000Z")!!
        val to = parseDateMillis("2026-10-31T23:59:59.999Z")!!
        val summary = e.sales.getSalesSummaryBetween(from, to)

        assertEquals(2, summary.salesCount)
        assertEquals(450.0, summary.revenue, 0.0)
        assertEquals(100.0, summary.margin!!, 0.0)
    }
}
