package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private val SK_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

private fun skItem(id: String, name: String, qty: Double, cost: Double) =
    SaleItem(id, name, cost * 2, cost, qty, qty * cost * 2)

private fun skSale(id: String, createdAt: String, vararg items: SaleItem): Sale {
    val total = items.sumOf { it.total }
    return Sale(id, createdAt, items.toList(), total, 0.0, total, PaymentMethod.Cash, total, 0.0, 0.0, null, null, null)
}

private fun skProduct(id: String, name: String, purchase: Double, sale: Double, stock: Int) =
    Product(id, name, purchase, sale, stock, 2, null, null, null)

private val SK_PRODUCTS = listOf(
    skProduct("a", "Riz", 600.0, 1000.0, 5),
    skProduct("b", "Huile", 500.0, 800.0, 0),
    skProduct("c", "Sel", 100.0, 150.0, 10),
    skProduct("d", "Savon", 200.0, 300.0, 4),
    skProduct("e", "Sucre", 10.0, 15.0, 500)
)

private fun skRows() = activeStatRows(
    listOf(
        skSale(
            "s1", "2026-10-05T10:00:00.000Z",
            skItem("a", "Riz", 30.0, 600.0),
            skItem("b", "Huile", 15.0, 500.0),
            skItem("e", "Sucre", 30.0, 10.0)
        ),
        skSale("s2", "2026-07-11T12:00:00.000Z", skItem("d", "Savon", 1.0, 200.0))
    )
)

class StatsStockTest {

    @Test fun aucunProduitDonneNull() {
        assertNull(computeStock(skRows(), emptyList(), SK_NOW))
    }

    @Test fun rotation() {
        val s = computeStock(skRows(), SK_PRODUCTS, SK_NOW)!!
        assertEquals(9800.0, s.stockCost, 1e-6)
        assertEquals(10.67687, s.turnover, 1e-4)
        assertEquals(34.186, s.daysOfStock, 1e-2)
        assertEquals(12.6263, s.sellThrough, 1e-3)
        assertEquals(true, s.hasSales90)
        assertEquals(RotationLevel.VeryGood, rotationLevel(s.turnover))
        assertEquals(RotationLevel.Good, rotationLevel(3.0))
        assertEquals(RotationLevel.Weak, rotationLevel(1.5))
        assertEquals(RotationLevel.VeryWeak, rotationLevel(1.0))
    }

    @Test fun rupturesAPrevoir() {
        val s = computeStock(skRows(), SK_PRODUCTS, SK_NOW)!!
        assertEquals(listOf("Huile", "Riz"), s.risks.map { it.name })
        assertEquals(15.0, s.risks[0].toBuy, 1e-9)
        assertEquals(25.0, s.risks[1].toBuy, 1e-9)
        assertEquals(5.0, s.risks[1].cover, 1e-9)
        assertEquals(2800.0, s.lostWeekly!!, 1e-6)
    }

    @Test fun dormantEtSurStock() {
        val s = computeStock(skRows(), SK_PRODUCTS, SK_NOW)!!
        assertEquals(listOf("Sel", "Savon"), s.dormant.map { it.name })
        assertNull(s.dormant[0].sinceDays)
        assertEquals(100, s.dormant[1].sinceDays)
        assertEquals(2, s.dormantCount)
        assertEquals(1800.0, s.dormantValue, 1e-6)
        assertEquals(listOf("Sucre"), s.overstock.map { it.name })
        assertEquals(500L, s.overstock[0].days)
    }

    @Test fun sansVentesPasDeRotation() {
        val s = computeStock(emptyList(), SK_PRODUCTS, SK_NOW)!!
        assertEquals(false, s.hasSales90)
        assertEquals(0.0, s.turnover, 1e-9)
        assertNull(s.lostWeekly)
        assertEquals(emptyList<StockRisk>(), s.risks)
    }
}
