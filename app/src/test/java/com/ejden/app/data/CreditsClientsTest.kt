package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private val NOW_C: Instant = Instant.parse("2026-10-09T12:00:00Z")

private fun credSale(
    id: String,
    createdAt: String,
    remaining: Double,
    customerId: String? = null,
    borrower: String? = null
) = Sale(
    id, createdAt, listOf(SaleItem("p", "P", remaining, null, 1.0, remaining)),
    remaining, 0.0, remaining, PaymentMethod.Credit, 0.0, 0.0, remaining, customerId, borrower
)

private val AWA_C = Client("c1", "Awa Koné", "", "2026-01-01T10:00:00.000Z")

class CreditsClientsTest {

    @Test
    fun formulaireClient() {
        val others = listOf(AWA_C)

        val empty = validateClientForm("   ", "", others, null) as ClientFormResult.Invalid
        assertEquals("Veuillez saisir le nom du client.", empty.message)
        assertEquals(ClientField.Name, empty.field)

        val long = validateClientForm("x".repeat(81), "", others, null) as ClientFormResult.Invalid
        assertEquals("Nom trop long (80 caractères max).", long.message)

        val phone = validateClientForm("Bob", "abc", others, null) as ClientFormResult.Invalid
        assertEquals("Téléphone invalide : chiffres, +, espaces et tirets seulement.", phone.message)
        assertEquals(ClientField.Phone, phone.field)

        val dup = validateClientForm("  awa   KONÉ ", "", others, null) as ClientFormResult.Invalid
        assertEquals("Un client porte déjà ce nom.", dup.message)

        // En modification, le client lui-même ne compte pas comme doublon.
        val same = validateClientForm("Awa Koné", "07 00", others, "c1") as ClientFormResult.Valid
        assertEquals("Awa Koné", same.name)
        assertEquals("07 00", same.phone)

        val ok = validateClientForm("  Bob   Dupont ", " +225 07 ", others, null) as ClientFormResult.Valid
        assertEquals("Bob Dupont", ok.name)
        assertEquals("+225 07", ok.phone)
    }

    @Test
    fun resumeDesCreditsParDebiteur() {
        val sales = listOf(
            credSale("s1", "2026-08-30T12:00:00.000Z", 1000.0, customerId = "c1"),
            credSale("s2", "2026-10-01T12:00:00.000Z", 500.0, borrower = "awa koné"),
            credSale("s3", "2026-10-04T12:00:00.000Z", 300.0, borrower = "Bob"),
            credSale("s4", "2026-10-08T12:00:00.000Z", 200.0, borrower = "BOB"),
            credSale("s5", "2026-10-05T12:00:00.000Z", 0.0, customerId = "c1"),
            credSale("s6", "2026-10-06T12:00:00.000Z", 700.0)
        )
        val payments = listOf(
            CreditPayment("pay1", "s1", 200.0, PaymentMethod.Cash, "2026-09-01T10:00:00.000Z")
        )

        val summary = computeCreditsSummary(sales, payments, listOf(AWA_C), NOW_C)

        assertEquals(2, summary.debtors)
        assertEquals(1800.0, summary.total, 0.0)
        assertEquals(1300.0, summary.overdueAmount, 0.0)
        assertEquals(1, summary.overdueDebtors)

        val awa = summary.groups[0]
        assertEquals("c1", awa.clientId)
        assertEquals("Awa Koné", awa.name)
        assertEquals(1300.0, awa.amount, 0.0)
        assertEquals(listOf("s1", "s2"), awa.sales.map { it.sale.id })
        assertEquals(40, awa.oldestDays)
        assertTrue(awa.overdue)

        val bob = summary.groups[1]
        assertEquals("name:bob", bob.key)
        assertEquals("BOB", bob.name)
        assertEquals(500.0, bob.amount, 0.0)
        assertEquals(5, bob.oldestDays)
        assertFalse(bob.overdue)
    }

    @Test
    fun dettesEtPaiementsDUnClientCoherentsAvecLeDepot() {
        val store = InMemoryStore()
        val products = ProductRepository(store)
        val sales = SaleRepository(store, products)
        val credits = CreditPaymentRepository(store, sales)
        val clients = ClientRepository(store, sales, credits)

        clients.addClient(AWA_C)
        sales.saveSales(
            listOf(
                credSale("s1", "2026-10-01T12:00:00.000Z", 1000.0, customerId = "c1"),
                credSale("s2", "2026-10-05T12:00:00.000Z", 500.0, borrower = " AWA koné "),
                credSale("s3", "2026-10-06T12:00:00.000Z", 300.0, customerId = "autre")
            )
        )
        credits.recordCreditPayment(listOf("s1"), 100.0, PaymentMethod.Cash)
        credits.recordCreditPayment(listOf("s2"), 50.0, PaymentMethod.Card)

        val allSales = sales.getSales()
        val allPayments = credits.getCreditPayments()
        val remaining = computeRemainingMap(allSales, allPayments)

        val repoDebt = clients.getClientDebt(AWA_C)
        assertEquals(repoDebt.sales.map { it.id }, clientDebtSales(AWA_C, allSales, remaining).map { it.id })
        assertEquals(
            clients.getClientPayments(AWA_C, 5).map { it.id },
            clientRecentPayments(AWA_C, allSales, allPayments, 5).map { it.id }
        )
        assertEquals(1, clientRecentPayments(AWA_C, allSales, allPayments, 1).size)
    }

    @Test
    fun messagesDEncaissement() {
        val ok = describePayment(CreditPaymentResult.Ok(emptyList()), 500.0, 500.0)
        assertTrue(ok.success)
        assertEquals("Crédit soldé : 500 FCFA reçus.", ok.message)

        val partial = describePayment(CreditPaymentResult.Ok(emptyList()), 200.0, 500.0)
        assertEquals("Paiement de 200 FCFA enregistré. Reste 300 FCFA.", partial.message)

        assertEquals(
            "Ce montant dépasse ce qui est dû (500 FCFA).",
            describePayment(CreditPaymentResult.TooMuch(500.0), 900.0, 500.0).message
        )
        assertEquals("Ce crédit est déjà soldé.", describePayment(CreditPaymentResult.NoDebt, 1.0, 0.0).message)
        assertEquals(
            "Enregistrement impossible : la mémoire est pleine.",
            describePayment(CreditPaymentResult.StorageError, 1.0, 1.0).message
        )
        assertFalse(describePayment(CreditPaymentResult.InvalidAmount, 0.0, 1.0).success)
    }
}
