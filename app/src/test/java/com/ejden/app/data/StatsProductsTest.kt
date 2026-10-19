package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

private val PD_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

private fun pdItem(id: String, name: String, qty: Double, unitPrice: Double) =
    SaleItem(id, name, unitPrice, null, qty, qty * unitPrice)

private fun pdSale(id: String, createdAt: String, vararg items: SaleItem): Sale {
    val total = items.sumOf { it.total }
    return Sale(id, createdAt, items.toList(), total, 0.0, total, PaymentMethod.Cash, total, 0.0, 0.0, null, null, null)
}

private fun pdProduct(id: String, name: String, purchase: Double, sale: Double, category: String?) =
    Product(id, name, purchase, sale, 10, 2, null, null, category)

private val PD_PRODUCTS = listOf(
    pdProduct("p1", "Riz", 600.0, 1000.0, "Alimentaire"),
    pdProduct("p2", "Huile", 500.0, 450.0, "Alimentaire"),
    pdProduct("p3", "Sel", 480.0, 500.0, null),
    pdProduct("p4", "Savon", 0.0, 200.0, null)
)

private fun pdRows() = activeStatRows(
    listOf(
        pdSale(
            "s1", "2026-10-10T10:00:00.000Z",
            pdItem("p1", "Riz", 8.0, 1000.0),
            pdItem("p2", "Huile", 3.0, 500.0),
            pdItem("p3", "Sel", 1.0, 500.0)
        ),
        pdSale(
            "s2", "2026-09-05T10:00:00.000Z",
            pdItem("p1", "Riz", 4.0, 1000.0),
            pdItem("p4", "Savon", 5.0, 200.0)
        )
    )
)

class StatsProductsTest {

    @Test fun aucuneVenteSurLaPeriode() {
        val old = activeStatRows(listOf(pdSale("o", "2026-01-01T10:00:00.000Z", pdItem("p1", "Riz", 1.0, 1000.0))))
        assertNull(computeProducts(old, PD_PRODUCTS, 30, PD_NOW))
    }

    @Test fun analyseAbc() {
        val abc = computeProducts(pdRows(), PD_PRODUCTS, 30, PD_NOW)!!.abc
        assertEquals(1, abc.a.count)
        assertEquals(80.0, abc.a.sharePct, 1e-9)
        assertEquals(1, abc.b.count)
        assertEquals(15.0, abc.b.sharePct, 1e-9)
        assertEquals(1, abc.c.count)
        assertEquals(5.0, abc.c.sharePct, 1e-9)
        assertEquals(1, abc.unsold)
        assertEquals(listOf("Riz"), abc.topA.map { it.name })
    }

    @Test fun categories() {
        val cats = computeProducts(pdRows(), PD_PRODUCTS, 30, PD_NOW)!!.categories
        assertEquals(listOf("Alimentaire", "Sans catégorie"), cats.map { it.name })
        assertEquals(9500.0, cats[0].amount, 1e-9)
        assertEquals(500.0 / 9500.0, cats[1].ratio, 1e-9)
    }

    @Test fun hausseEtBaisse() {
        val data = computeProducts(pdRows(), PD_PRODUCTS, 30, PD_NOW)!!
        assertEquals(listOf("Riz", "Huile"), data.risers.map { it.name })
        assertEquals(100.0, data.risers[0].change, 1e-9)
        assertEquals(4.0, data.risers[0].before, 1e-9)
        assertEquals(8.0, data.risers[0].now, 1e-9)
        assertEquals(listOf("Savon"), data.fallers.map { it.name })
        assertEquals(-100.0, data.fallers[0].change, 1e-9)
        assertEquals(5.0, data.fallers[0].before, 1e-9)
    }

    @Test fun prixARevoir() {
        val data = computeProducts(pdRows(), PD_PRODUCTS, 30, PD_NOW)!!
        assertEquals(listOf("Huile"), data.lossMakers.map { it.name })
        assertEquals(listOf("Sel"), data.thin.map { it.name })
        assertEquals(4.0, data.thin[0].marginPct, 1e-9)
    }
}
