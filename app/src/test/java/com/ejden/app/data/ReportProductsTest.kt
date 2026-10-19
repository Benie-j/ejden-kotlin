package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val RP_ZONE = ZoneOffset.UTC
private val RP_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RP_PREV = ReportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))

private val RP_PRODUCTS = listOf(
    Product("p1", "Riz", 600.0, 1000.0, 10, 5, null, null, "Alimentaire"),
    Product("p2", "Eau", 300.0, 500.0, 3, 5, null, null, "Boissons"),
    Product("p3", "Sel", 100.0, 200.0, 0, 2, null, null, null),
    Product("p4", "Thé", 200.0, 400.0, 8, 2, null, null, "Boissons")
)

private fun rpCategories(products: List<Product>) = products.associate { it.id to (it.category ?: "") }

private fun rpItem(id: String, name: String, qty: Double, price: Double, cost: Double?) =
    SaleItem(id, name, price, cost, qty, qty * price)

private fun rpSale(
    id: String,
    createdAt: String,
    cancelled: String?,
    vararg items: SaleItem
): Sale {
    val total = items.sumOf { it.total }
    return Sale(
        id, createdAt, items.toList(), total, 0.0, total, PaymentMethod.Cash,
        total, 0.0, 0.0, null, null, cancelled
    )
}

private val RP_SALES = listOf(
    rpSale("s1", "2026-10-05T12:00:00.000Z", null,
        rpItem("p1", "Riz", 2.0, 1000.0, 600.0), rpItem("p2", "Eau", 1.0, 500.0, 300.0)),
    rpSale("s2", "2026-10-06T12:00:00.000Z", null,
        rpItem("p1", "Riz", 1.0, 1000.0, 600.0)),
    rpSale("s3", "2026-10-07T12:00:00.000Z", "2026-10-08T09:00:00.000Z",
        rpItem("p2", "Eau", 4.0, 500.0, 300.0)),
    rpSale("s4", "2026-09-15T12:00:00.000Z", null,
        rpItem("p1", "Riz", 1.0, 1000.0, 600.0))
)

private val RP_MOVES = listOf(
    StockMovement("m1", "p1", "Riz", StockMovementType.In, 5, 5, 10, "Réapprovisionnement", "2026-10-04T08:00:00.000Z"),
    StockMovement("m2", "p2", "Eau", StockMovementType.Out, -2, 5, 3, "Casse", "2026-10-06T08:00:00.000Z"),
    StockMovement("m3", "p1", "Riz", StockMovementType.In, 10, 10, 20, "Réapprovisionnement", "2026-09-20T08:00:00.000Z"),
    StockMovement("m4", "p4", "Thé", StockMovementType.Adjust, -1, 9, 8, "Inventaire", "2026-10-10T08:00:00.000Z")
)

private fun rpProducts(withPrevious: Boolean = true): ReportProducts {
    val filters = ReportFilters(RP_RANGE)
    val cats = rpCategories(RP_PRODUCTS)
    val views = buildSaleViews(RP_SALES, filters, RP_RANGE, cats, RP_ZONE)
    val prev = if (withPrevious) buildSaleViews(RP_SALES, filters, RP_PREV, cats, RP_ZONE) else null
    return computeReportProducts(views, prev, RP_PRODUCTS, filters, RP_RANGE, RP_ZONE)
}

private fun rpStock(history: Int, filters: ReportFilters = ReportFilters(RP_RANGE)): ReportStock =
    computeReportStock(RP_PRODUCTS, RP_MOVES, rpProducts().soldQty, filters, RP_RANGE, history, RP_ZONE)

class ReportProductsTest {
    @Test
    fun ranksProductsByRevenueAndIgnoresCancelledSales() {
        val r = rpProducts()
        assertEquals(listOf("p1", "p2"), r.rows.map { it.id })
        val riz = r.rows[0]
        assertEquals(3.0, riz.qty, 1e-9)
        assertEquals(3000.0, riz.amount, 1e-9)
        assertEquals(3000.0 / 3500.0 * 100, riz.share, 1e-9)
        assertEquals(2.0 / 19.0, riz.frequency, 1e-9)
        assertEquals("Alimentaire", riz.category)
        assertNull(riz.abc)
        assertEquals(1.0, r.rows[1].qty, 1e-9)
    }

    @Test
    fun previousQuantityIsNullWhenComparisonUnavailable() {
        val with = rpProducts(true)
        assertEquals(1.0, with.rows[0].prevQty!!, 1e-9)
        assertEquals(0.0, with.rows[1].prevQty!!, 1e-9)
        val without = rpProducts(false)
        assertNull(without.rows[0].prevQty)
    }

    @Test
    fun listsProductsWithoutSale() {
        assertEquals(listOf("p3", "p4"), rpProducts().noSale.map { it.id })
        val filters = ReportFilters(RP_RANGE, productId = "p3")
        val views = buildSaleViews(RP_SALES, filters, RP_RANGE, rpCategories(RP_PRODUCTS), RP_ZONE)
        val r = computeReportProducts(views, null, RP_PRODUCTS, filters, RP_RANGE, RP_ZONE)
        assertEquals(emptyList<String>(), r.rows.map { it.id })
        assertEquals(listOf("p3"), r.noSale.map { it.id })
    }

    @Test
    fun abcClassificationNeedsTenProducts() {
        val amounts = listOf(5000.0, 2000.0, 1000.0, 500.0, 400.0, 300.0, 200.0, 100.0, 50.0, 25.0)
        val products = amounts.indices.map {
            Product("q$it", "Produit $it", 0.0, amounts[it], 1, 1, null, null, null)
        }
        val items = amounts.indices.map { rpItem("q$it", "Produit $it", 1.0, amounts[it], null) }
        val sale = rpSale("s1", "2026-10-05T12:00:00.000Z", null, *items.toTypedArray())
        val filters = ReportFilters(RP_RANGE)
        val views = buildSaleViews(listOf(sale), filters, RP_RANGE, rpCategories(products), RP_ZONE)
        val r = computeReportProducts(views, null, products, filters, RP_RANGE, RP_ZONE)
        assertEquals(
            listOf("A", "A", "A", "B", "B", "B", "C", "C", "C", "C"),
            r.rows.map { it.abc }
        )
        assertEquals("Sans catégorie", r.rows[0].category)
    }

    @Test
    fun stockRowsComputeRateDaysLeftAndStatus() {
        val s = rpStock(history = 19)
        val rows = s.rows.associateBy { it.id }
        val riz = rows.getValue("p1")
        assertEquals(ReportStockStatus.Ok, riz.status)
        assertEquals(3.0 / 19.0, riz.dailyRate!!, 1e-9)
        assertEquals(190.0 / 3.0, riz.daysLeft!!, 1e-9)
        assertEquals(0.3, riz.rotation!!, 1e-9)
        assertEquals(ReportStockStatus.Low, rows.getValue("p2").status)
        assertEquals(57.0, rows.getValue("p2").daysLeft!!, 1e-9)
        val sel = rows.getValue("p3")
        assertEquals(ReportStockStatus.Out, sel.status)
        assertNull(sel.dailyRate)
        assertNull(sel.rotation)
        assertEquals(8500.0, s.value, 1e-9)
        assertEquals((0.3 + 1.0 / 3.0) / 2, s.rotation!!, 1e-9)
    }

    @Test
    fun noRateBeforeSevenDaysOfHistory() {
        val s = rpStock(history = 5)
        assertNull(s.rows.first { it.id == "p1" }.dailyRate)
        assertNull(s.rows.first { it.id == "p1" }.daysLeft)
    }

    @Test
    fun movementsAreFilteredSortedAndSummed() {
        val s = rpStock(history = 19)
        assertEquals(listOf("Thé", "Eau", "Riz"), s.movements.map { it.product })
        assertEquals(listOf("Ajustement", "Sortie", "Entrée"), s.movements.map { it.type })
        assertEquals(5, s.entries)
        assertEquals(3, s.exits)
        val boissons = rpStock(19, ReportFilters(RP_RANGE, category = "Boissons"))
        assertEquals(listOf("Thé", "Eau"), boissons.movements.map { it.product })
        val riz = rpStock(19, ReportFilters(RP_RANGE, productId = "p1"))
        assertEquals(listOf("Riz"), riz.movements.map { it.product })
    }
}
