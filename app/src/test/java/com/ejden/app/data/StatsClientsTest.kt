package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val CL_NOW = Instant.parse("2026-10-19T12:00:00Z").toEpochMilli()

private fun clItem(id: String, name: String, qty: Double, price: Double) =
    SaleItem(id, name, price, null, qty, qty * price)

private fun clSale(
    id: String,
    createdAt: String,
    customer: String?,
    method: PaymentMethod,
    vararg items: SaleItem
): Sale {
    val total = items.sumOf { it.total }
    return Sale(id, createdAt, items.toList(), total, 0.0, total, method, total, 0.0, 0.0, customer, null, null)
}

private val CL_CLIENTS = listOf(
    Client("c1", "Awa", "0100000001", "2026-01-01T10:00:00.000Z"),
    Client("c2", "Binta", "0100000002", "2026-01-01T10:00:00.000Z"),
    Client("c3", "Cheikh", "0100000003", "2026-01-01T10:00:00.000Z")
)

private fun clRows() = activeStatRows(
    listOf(
        clSale("s1", "2026-10-10T10:00:00.000Z", "c1", PaymentMethod.Cash,
            clItem("p1", "Riz", 2.0, 1000.0), clItem("p2", "Huile", 1.0, 500.0)),
        clSale("s2", "2026-10-12T10:00:00.000Z", "c1", PaymentMethod.Cash,
            clItem("p1", "Riz", 1.0, 1000.0), clItem("p2", "Huile", 1.0, 500.0)),
        clSale("s3", "2026-10-14T10:00:00.000Z", "c2", PaymentMethod.Credit,
            clItem("p1", "Riz", 1.0, 1000.0)),
        clSale("s4", "2026-08-01T10:00:00.000Z", "c3", PaymentMethod.Cash,
            clItem("p3", "Sel", 1.0, 500.0)),
        clSale("s5", "2026-08-10T10:00:00.000Z", "c3", PaymentMethod.Cash,
            clItem("p3", "Sel", 1.0, 500.0)),
        clSale("s6", "2026-10-15T10:00:00.000Z", null, PaymentMethod.Cash,
            clItem("p3", "Sel", 1.0, 500.0)),
        clSale("s7", "2026-10-16T10:00:00.000Z", "zz", PaymentMethod.Cash,
            clItem("p3", "Sel", 1.0, 500.0))
    )
)

class StatsClientsTest {

    @Test fun aucuneVenteSurLaPeriode() {
        assertNull(computeClients(clRows(), CL_CLIENTS, 1, CL_NOW))
    }

    @Test fun paniers() {
        val b = computeClients(clRows(), CL_CLIENTS, 90, CL_NOW)!!.baskets
        assertEquals(1000.0, b.mean, 1e-9)
        assertEquals(500.0, b.median, 1e-9)
        assertEquals(2500.0, b.maxSale, 1e-9)
        assertEquals(10.0 / 7, b.itemsPerSale, 1e-9)
        assertEquals(1000.0 / 7000.0 * 100, b.creditSharePct, 1e-9)
    }

    @Test fun produitsAchetesEnsemble() {
        val p = computeClients(clRows(), CL_CLIENTS, 90, CL_NOW)!!.pairs
        assertEquals(1, p.top.size)
        assertEquals("Riz", p.top[0].a)
        assertEquals("Huile", p.top[0].b)
        assertEquals(2, p.top[0].count)
        assertEquals(2.0 / 7 * 100, p.top[0].sharePct, 1e-9)
        assertEquals(2.0 / 7 * 100, p.multiSharePct, 1e-9)
    }

    @Test fun clientsIdentifies() {
        val i = computeClients(clRows(), CL_CLIENTS, 90, CL_NOW)!!.identified
        assertNotNull(i)
        assertEquals(3, i!!.active)
        assertEquals(3, i.fresh)
        assertEquals(0, i.returning)
        assertEquals(2.0 / 3 * 100, i.repeatPct, 1e-9)
        assertEquals(5.0 / 7 * 100, i.identifiedPct, 1e-9)
        assertEquals(listOf("Awa", "Binta", "Cheikh"), i.top.map { it.name })
        assertEquals(4000.0, i.top[0].amount, 1e-9)
        assertEquals(2, i.top[0].count)
        assertEquals(100.0, i.topSharePct, 1e-9)
    }

    @Test fun clientsARelancer() {
        val i = computeClients(clRows(), CL_CLIENTS, 90, CL_NOW)!!.identified!!
        assertEquals(1, i.sleeping.size)
        assertEquals("Cheikh", i.sleeping[0].name)
        assertEquals(70, i.sleeping[0].daysSince)
        assertEquals(2, i.sleeping[0].count)
    }

    @Test fun periodeCourteEtSansFiche() {
        val short = computeClients(clRows(), CL_CLIENTS, 30, CL_NOW)!!.identified!!
        assertEquals(2, short.active)
        assertTrue(short.sleeping.isEmpty())
        assertNull(computeClients(clRows(), emptyList(), 90, CL_NOW)!!.identified)
    }
}
