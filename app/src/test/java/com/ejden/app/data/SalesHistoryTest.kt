package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private val TODAY_H: LocalDate = LocalDate.of(2026, 10, 9)

private fun histSale(
    id: String,
    createdAt: String,
    total: Double = 1000.0,
    remaining: Double = 0.0,
    borrower: String? = null,
    cancelledAt: String? = null,
    name: String = "Riz",
    method: PaymentMethod = PaymentMethod.Cash,
    cost: Double? = 600.0,
    discount: Double = 0.0
) = Sale(
    id, createdAt, listOf(SaleItem("p", name, total, cost, 1.0, total)),
    total, discount, total - discount, method, total - remaining, 0.0, remaining, null, borrower, cancelledAt
)

class SalesHistoryTest {

    private val s1 = histSale("s1-aaaaaa", "2026-10-09T09:00:00.000Z")
    private val s2 = histSale("s2-bbbbbb", "2026-10-08T15:00:00.000Z", remaining = 500.0, borrower = "Awa", name = "Sucre", method = PaymentMethod.Credit)
    private val s3 = histSale("s3-cccccc", "2026-10-07T10:00:00.000Z", cancelledAt = "2026-10-07T11:00:00.000Z")
    private val all = listOf(s3, s1, s2)
    private val remaining = mapOf("s1-aaaaaa" to 0.0, "s2-bbbbbb" to 500.0, "s3-cccccc" to 0.0)

    private fun filter(f: HistoryFilter, q: String = "") =
        filterSales(all, remaining, f, q, TODAY_H, ZoneOffset.UTC).map { it.id }

    @Test
    fun filtresEtTriRecentDAbord() {
        assertEquals(listOf("s1-aaaaaa", "s2-bbbbbb", "s3-cccccc"), filter(HistoryFilter.All))
        assertEquals(listOf("s1-aaaaaa"), filter(HistoryFilter.Today))
        assertEquals(listOf("s1-aaaaaa"), filter(HistoryFilter.Paid))
        assertEquals(listOf("s2-bbbbbb"), filter(HistoryFilter.Credit))
        assertEquals(listOf("s3-cccccc"), filter(HistoryFilter.Cancelled))
    }

    @Test
    fun rechercheParProduitClientNumeroEtPaiement() {
        assertEquals(listOf("s2-bbbbbb"), filter(HistoryFilter.All, "sucre"))
        assertEquals(listOf("s2-bbbbbb"), filter(HistoryFilter.All, "awa"))
        assertEquals(listOf("s1-aaaaaa"), filter(HistoryFilter.All, "n° aaaaaa"))
        assertEquals(listOf("s2-bbbbbb"), filter(HistoryFilter.All, "crédit"))
        assertTrue(filter(HistoryFilter.All, "introuvable").isEmpty())
    }

    @Test
    fun resumeSansLesVentesAnnulees() {
        val sorted = filterSales(all, remaining, HistoryFilter.All, "", TODAY_H, ZoneOffset.UTC)
        val summary = summarizeHistory(sorted, remaining)
        assertEquals(2, summary.count)
        assertEquals(2000.0, summary.total, 0.0)
        assertEquals(500.0, summary.remaining, 0.0)
    }

    @Test
    fun groupesParJour() {
        val sorted = filterSales(all, remaining, HistoryFilter.All, "", TODAY_H, ZoneOffset.UTC)
        val groups = groupSalesByDay(sorted, TODAY_H, ZoneOffset.UTC)
        assertEquals(listOf("Aujourd'hui", "Hier"), groups.take(2).map { it.label })
        assertEquals(3, groups.size)
        assertEquals("Mercredi 7 Octobre 2026", groups[2].label)
        assertEquals("Date inconnue", groupSalesByDay(listOf(histSale("x", "pas une date")), TODAY_H, ZoneOffset.UTC)[0].label)
    }

    @Test
    fun formatsDeDates() {
        assertEquals("09:05", timeText("2026-10-09T09:05:00.000Z", ZoneOffset.UTC))
        assertEquals("09/10/2026", shortDateText("2026-10-09T09:05:00.000Z", ZoneOffset.UTC))
        assertEquals("9 octobre 2026 à 09:05", fullDateText("2026-10-09T09:05:00.000Z", ZoneOffset.UTC))
        assertEquals("N° AAAAAA", saleNumber(s1))
    }

    @Test
    fun beneficeDUneVente() {
        assertEquals(400.0, saleMargin(histSale("a", "2026-10-09T09:00:00.000Z"))!!, 0.0)
        assertEquals(350.0, saleMargin(histSale("b", "2026-10-09T09:00:00.000Z", discount = 50.0))!!, 0.0)
        assertNull(saleMargin(histSale("c", "2026-10-09T09:00:00.000Z", cost = null)))
    }

    @Test
    fun texteDuRecu() {
        val sale = histSale(
            "s2-bbbbbb", "2026-10-09T09:05:00.000Z", total = 2000.0, remaining = 1500.0,
            borrower = "Awa", name = "Sucre", method = PaymentMethod.Credit, discount = 0.0
        )
        val receipt = buildReceipt(sale, ShopSettings("Chez Awa", "07 00"), remainingNow = 1000.0, zone = ZoneOffset.UTC)
        val lines = receipt.split("\n")

        assertEquals("*Chez Awa*", lines[0])
        assertEquals("Tél : 07 00", lines[1])
        assertTrue(lines.contains("*REÇU DE VENTE*"))
        assertTrue(lines.contains("N° BBBBBB"))
        assertTrue(lines.contains("9 octobre 2026 à 09:05"))
        assertTrue(lines.contains("Client : Awa"))
        assertTrue(lines.contains("1 \u00D7 Sucre \u2014 2\u202F000 FCFA"))
        assertTrue(lines.contains("*TOTAL : 2\u202F000 FCFA*"))
        assertTrue(lines.contains("Paiement : Crédit"))
        assertTrue(lines.contains("Payé : 500 FCFA"))
        assertTrue(lines.contains("Déjà remboursé : 500 FCFA"))
        assertTrue(lines.contains("*Reste à payer : 1\u202F000 FCFA*"))
        assertEquals("Merci de votre confiance !", lines.last())

        val settled = buildReceipt(sale, ShopSettings("", ""), remainingNow = 0.0, zone = ZoneOffset.UTC)
        assertTrue(settled.split("\n").contains("*Crédit soldé*"))
        assertEquals("*REÇU DE VENTE*", settled.split("\n")[0])
    }
}
