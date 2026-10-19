package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val RV_ZONE = ZoneOffset.UTC
private val RV_RANGE = ReportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 19))
private val RV_CATEGORIES = mapOf("p1" to "Alimentaire", "p2" to "Boissons")

private fun rvItem(id: String, name: String, qty: Double, price: Double, cost: Double?) =
    SaleItem(id, name, price, cost, qty, qty * price)

private fun rvSale(
    id: String,
    createdAt: String,
    customer: String?,
    method: PaymentMethod,
    remaining: Double,
    cancelled: String?,
    vararg items: SaleItem
): Sale {
    val total = items.sumOf { it.total }
    return Sale(
        id, createdAt, items.toList(), total, 0.0, total, method,
        total - remaining, 0.0, remaining, customer, null, cancelled
    )
}

private val RV_SALES = listOf(
    rvSale("s1", "2026-10-05T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rvItem("p1", "Riz", 2.0, 1000.0, 600.0), rvItem("p2", "Eau", 1.0, 500.0, 300.0)),
    rvSale("s2", "2026-10-06T12:00:00.000Z", "c2", PaymentMethod.Credit, 400.0, null,
        rvItem("p1", "Riz", 1.0, 1000.0, 600.0)),
    rvSale("s3", "2026-10-07T12:00:00.000Z", "c1", PaymentMethod.MobileMoney, 0.0, "2026-10-08T09:00:00.000Z",
        rvItem("p2", "Eau", 4.0, 500.0, null)),
    rvSale("s4", "2026-09-15T12:00:00.000Z", "c1", PaymentMethod.Cash, 0.0, null,
        rvItem("p1", "Riz", 1.0, 1000.0, 600.0)),
    rvSale("s5", "pas une date", "c1", PaymentMethod.Cash, 0.0, null,
        rvItem("p1", "Riz", 1.0, 1000.0, 600.0))
)

private fun rvIds(filters: ReportFilters) =
    buildSaleViews(RV_SALES, filters, filters.range, RV_CATEGORIES, RV_ZONE).map { it.sale.id }

class ReportViewsTest {
    private val base = ReportFilters(RV_RANGE)

    @Test fun filtresParDefautGardentLesAnnuleesMaisPasHorsPeriode() {
        assertEquals(listOf("s1", "s2", "s3"), rvIds(base))
        assertEquals(listOf("s1", "s2", "s3", "s4"), rvIds(base.copy(range = ReportRange(LocalDate.of(2026, 9, 1), RV_RANGE.end))))
    }

    @Test fun filtresMoyenClientStatut() {
        assertEquals(listOf("s2"), rvIds(base.copy(method = PaymentMethod.Credit)))
        assertEquals(listOf("s1", "s3"), rvIds(base.copy(clientId = "c1")))
        assertEquals(listOf("s3"), rvIds(base.copy(status = ReportStatus.Cancelled)))
        assertEquals(listOf("s2"), rvIds(base.copy(status = ReportStatus.Credit)))
        assertEquals(listOf("s1", "s2", "s3"), rvIds(base.copy(status = ReportStatus.All)))
    }

    @Test fun filtreProduitRecalculeLeMontant() {
        val views = buildSaleViews(RV_SALES, base.copy(productId = "p2"), RV_RANGE, RV_CATEGORIES, RV_ZONE)
        assertEquals(listOf("s1", "s3"), views.map { it.sale.id })
        assertEquals(500.0, views[0].amount, 1e-9)
        assertEquals(1, views[0].lines.size)
        assertEquals(2000.0, views[1].amount, 1e-9)
        assertEquals(300.0, views[0].lines[0].cost!!, 1e-9)
        assertNull(views[1].lines[0].cost)
    }

    @Test fun filtreCategorie() {
        assertEquals(listOf("s1", "s3"), rvIds(base.copy(category = "Boissons")))
        val views = buildSaleViews(RV_SALES, base.copy(category = "Alimentaire"), RV_RANGE, RV_CATEGORIES, RV_ZONE)
        assertEquals(listOf("s1", "s2"), views.map { it.sale.id })
        assertEquals(2000.0, views[0].amount, 1e-9)
    }

    @Test fun totauxIgnorentLesAnnulees() {
        val t = computeTotals(buildSaleViews(RV_SALES, base, RV_RANGE, RV_CATEGORIES, RV_ZONE))
        assertEquals(3500.0, t.revenue, 1e-9)
        assertEquals(2, t.count)
        assertEquals(4.0, t.units, 1e-9)
        assertEquals(1750.0, t.average, 1e-9)
        assertEquals(1, t.cancelledCount)
        assertEquals(2000.0, t.cancelledAmount, 1e-9)
        assertEquals(1400.0, t.margin!!, 1e-9)
    }

    @Test fun margineInconnueSiPrixAchatManquant() {
        val unknown = listOf(
            rvSale("u", "2026-10-09T12:00:00.000Z", null, PaymentMethod.Cash, 0.0, null,
                rvItem("p2", "Eau", 1.0, 500.0, null))
        )
        val t = computeTotals(buildSaleViews(unknown, base, RV_RANGE, RV_CATEGORIES, RV_ZONE))
        assertEquals(1, t.count)
        assertNull(t.margin)
    }

    @Test fun totauxVides() {
        val t = computeTotals(emptyList())
        assertEquals(0.0, t.revenue, 1e-9)
        assertEquals(0, t.count)
        assertEquals(0.0, t.average, 1e-9)
        assertNull(t.margin)
    }

    @Test fun libellesDesMoyensDePaiement() {
        assertEquals("Espèces", reportMethodLabel(PaymentMethod.Cash))
        assertEquals("Mobile Money", reportMethodLabel(PaymentMethod.MobileMoney))
        assertEquals("Crédit", reportMethodLabel(PaymentMethod.Credit))
    }
}
