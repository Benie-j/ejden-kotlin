package com.ejden.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val STOCK_CLOCK: Instant = Instant.parse("2026-10-09T10:00:00Z")

private class FlakyMovements(private val inner: KeyValueStore) : KeyValueStore {
    override fun get(key: String) = inner.get(key)
    override fun set(key: String, value: String) = if (key == "ejden_stock_movements") false else inner.set(key, value)
    override fun remove(key: String) = inner.remove(key)
}

private class StockEnv(store: KeyValueStore = InMemoryStore()) {
    val products = ProductRepository(store)
    val sales = SaleRepository(store, products)
    val stock = StockRepository(store, products, sales, { STOCK_CLOCK })
}

private fun prod(id: String, name: String, stock: Int, threshold: Int = 0, barcode: String? = null, category: String? = null) =
    Product(id, name, 600.0, 1000.0, stock, threshold, barcode, null, category)

private fun failed(result: StockMovementResult) = (result as StockMovementResult.Failed).reason

class StockTest {

    @Test
    fun entreeSortieEtInventaire() {
        val env = StockEnv()
        env.products.addProduct(prod("p1", "Riz", 10))

        val entry = env.stock.recordMovement("p1", StockMovementType.In, 5.0, "Réapprovisionnement") as StockMovementResult.Ok
        assertEquals(15, entry.movement.after)
        assertEquals(5, entry.movement.quantity)
        assertEquals(15, env.products.getProductById("p1")!!.stock)

        val out = env.stock.recordMovement("p1", StockMovementType.Out, 4.0, "Casse") as StockMovementResult.Ok
        assertEquals(11, out.movement.after)
        assertEquals(-4, out.movement.quantity)

        val adjust = env.stock.recordMovement("p1", StockMovementType.Adjust, 8.0, "Inventaire") as StockMovementResult.Ok
        assertEquals(8, adjust.movement.after)
        assertEquals(-3, adjust.movement.quantity)
        assertEquals(8, env.products.getProductById("p1")!!.stock)

        assertEquals(3, env.stock.getMovements().size)
    }

    @Test
    fun refusDesMouvements() {
        val env = StockEnv()
        env.products.addProduct(prod("p1", "Riz", 10))
        fun record(type: StockMovementType, qty: Double, reason: String = "Autre") =
            failed(env.stock.recordMovement("p1", type, qty, reason))

        assertEquals(StockFailure.NotFound, failed(env.stock.recordMovement("zz", StockMovementType.In, 1.0, "Autre")))
        assertEquals(StockFailure.InvalidReason, record(StockMovementType.In, 1.0, "   "))
        assertEquals(StockFailure.InvalidQuantity, record(StockMovementType.In, Double.NaN))
        assertEquals(StockFailure.InvalidQuantity, record(StockMovementType.In, 0.0))
        assertEquals(StockFailure.InvalidQuantity, record(StockMovementType.In, 1.5))
        assertEquals(StockFailure.InvalidQuantity, record(StockMovementType.Adjust, -1.0))
        assertEquals(StockFailure.TooMuch, record(StockMovementType.Out, 11.0))
        assertEquals(StockFailure.TooHigh, record(StockMovementType.In, 1_000_000.0))
        assertEquals(StockFailure.TooHigh, record(StockMovementType.Adjust, 1_000_001.0))
        assertEquals(StockFailure.Unchanged, record(StockMovementType.Adjust, 10.0, "Inventaire"))

        assertEquals(10, env.products.getProductById("p1")!!.stock)
        assertTrue(env.stock.getMovements().isEmpty())
    }

    @Test
    fun echecDeLHistoriqueRemetLeStock() {
        val env = StockEnv(FlakyMovements(InMemoryStore()))
        env.products.addProduct(prod("p1", "Riz", 10))

        assertEquals(StockFailure.Storage, failed(env.stock.recordMovement("p1", StockMovementType.In, 5.0, "Autre")))
        assertEquals(10, env.products.getProductById("p1")!!.stock)
    }

    @Test
    fun messagesDErreur() {
        assertEquals("Produit introuvable.", stockErrorMessage(StockFailure.NotFound))
        assertEquals("Quantité invalide : un nombre entier est attendu.", stockErrorMessage(StockFailure.InvalidQuantity))
        assertEquals("Quantité supérieure au stock disponible.", stockErrorMessage(StockFailure.TooMuch))
        assertEquals("Quantité trop grande (1\u202F000\u202F000 max).", stockErrorMessage(StockFailure.TooHigh))
        assertEquals("Le stock compté est identique au stock actuel.", stockErrorMessage(StockFailure.Unchanged))
        assertEquals("Indiquez un motif.", stockErrorMessage(StockFailure.InvalidReason))
        assertEquals("Impossible d'enregistrer le mouvement.", stockErrorMessage(StockFailure.Storage))
    }

    @Test
    fun mouvementIllisibleEcarte() {
        val store = InMemoryStore()
        store.set(
            "ejden_stock_movements",
            JSONObject().put("id", "m1").put("productId", "p1").put("productName", "Riz")
                .put("type", "in").put("quantity", 5).put("before", 10).put("after", 99)
                .put("reason", "Autre").put("createdAt", "2026-10-09T10:00:00.000Z").let { "[$it]" }
        )
        val env = StockEnv(store)
        assertTrue(env.stock.getMovements().isEmpty())
        assertTrue(store.get("ejden_stock_movements_backup") != null)
    }

    @Test
    fun historiqueMelangeMouvementsVentesEtAnnulations() {
        val env = StockEnv()
        env.products.addProduct(prod("p1", "Riz", 10))
        env.stock.recordMovement("p1", StockMovementType.In, 5.0, "Réapprovisionnement")

        val sale = Sale(
            "sale-abcdef", "2026-10-09T09:00:00.000Z",
            listOf(SaleItem("p1", "Riz", 1000.0, 600.0, 2.0, 2000.0)),
            2000.0, 0.0, 2000.0, PaymentMethod.Cash, 2000.0, 0.0, 0.0, null, null,
            cancelledAt = "2026-10-09T09:30:00.000Z"
        )
        env.sales.saveSales(listOf(sale))

        val history = env.stock.getHistory()
        assertEquals(
            listOf("Réapprovisionnement", "Annulation vente N° ABCDEF", "Vente N° ABCDEF"),
            history.map { it.label }
        )
        assertEquals(5.0, history[0].delta, 0.0)
        assertEquals(15, history[0].after)
        assertEquals(2.0, history[1].delta, 0.0)
        assertEquals(-2.0, history[2].delta, 0.0)
        assertNull(history[2].after)

        assertEquals(3, env.stock.getHistory("p1").size)
        assertTrue(env.stock.getHistory("autre").isEmpty())
    }

    @Test
    fun valeurDuStock() {
        val value = getStockValue(listOf(prod("a", "A", 10), prod("b", "B", 5)))
        assertEquals(9000.0, value.cost, 0.0)
        assertEquals(15000.0, value.retail, 0.0)
    }

    @Test
    fun filtrageEtOrdreDeLaListe() {
        val riz = prod("1", "Riz", 0, 5, barcode = "611000123", category = "Cuisine")
        val sucre = prod("2", "Sucre", 3, 5)
        val sel = prod("3", "Sel", 50, 5, category = "Cuisine")
        val ail = prod("4", "Ail", 20)
        val all = listOf(sel, ail, sucre, riz)

        assertEquals(listOf("1", "2", "4", "3"), filterStockProducts(all, "", StockFilter.All).map { it.id })
        assertEquals(listOf("2"), filterStockProducts(all, "", StockFilter.Low).map { it.id })
        assertEquals(listOf("1"), filterStockProducts(all, "", StockFilter.Out).map { it.id })
        assertEquals(listOf("1"), filterStockProducts(all, "611 000", StockFilter.All).map { it.id })
        assertEquals(listOf("1", "3"), filterStockProducts(all, "cuisine", StockFilter.All).map { it.id })
        assertEquals(emptyList<String>(), filterStockProducts(all, "riz", StockFilter.Low).map { it.id })
    }

    @Test
    fun textesDeLHistorique() {
        assertEquals("+ 12", signedQuantity(12.0))
        assertEquals("\u2212 3", signedQuantity(-3.0))
        assertEquals("+ 1\u202F500", signedQuantity(1500.0))
        assertEquals("09/10/2026 09:05", stockDateTime("2026-10-09T09:05:00.000Z", ZoneOffset.UTC))
        assertEquals("", stockDateTime("n'importe quoi", ZoneOffset.UTC))
    }
}
