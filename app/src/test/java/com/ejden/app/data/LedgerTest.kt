package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

private val LEDGER_NOW: Instant = Instant.parse("2026-10-09T12:00:00Z")
private val LEDGER_CLOCK: Instant = Instant.parse("2026-10-09T10:00:00Z")

private class LedgerEnv {
    val store = InMemoryStore()
    val products = ProductRepository(store)
    val sales = SaleRepository(store, products) { LEDGER_CLOCK }
    val credits = CreditPaymentRepository(store, sales) { LEDGER_CLOCK }
    val clients = ClientRepository(store, sales, credits)
    val expenses = ExpenseRepository(store)
    val cash = CashRepository(store, sales, credits, expenses)
}

private fun ledgerSale(
    id: String,
    total: Double,
    remaining: Double = 0.0,
    createdAt: String = "2026-10-09T09:00:00.000Z",
    method: PaymentMethod = PaymentMethod.Cash,
    customerId: String? = null,
    borrower: String? = null,
    cancelledAt: String? = null
) = Sale(
    id, createdAt, listOf(SaleItem("p", "P", total, null, 1.0, total)),
    total, 0.0, total, method, total - remaining, 0.0, remaining,
    customerId, borrower, cancelledAt
)

class LedgerTest {

    // ---- Clients ----

    @Test
    fun clientsInvalidesEcartesEtNomsNettoyes() {
        val env = LedgerEnv()
        val raw = """[
            {"id":"c1","name":"  Awa   Koné ","phone":"07 00","createdAt":"2026-10-01T10:00:00.000Z"},
            {"id":"c2","name":"Bob","phone":"abc","createdAt":"2026-10-01T10:00:00.000Z"}
        ]"""
        env.store.set("ejden_clients", raw)

        val clients = env.clients.getClients()
        assertEquals(1, clients.size)
        assertEquals("Awa Koné", clients[0].name)
        assertEquals(raw, env.store.get("ejden_clients_backup"))
    }

    @Test
    fun ajoutModificationSuppressionEtUniciteDuNom() {
        val env = LedgerEnv()
        val awa = Client("c1", "Awa", "", "2026-10-01T10:00:00.000Z")

        assertTrue(env.clients.addClient(awa))
        assertTrue(env.clients.clientNameExists("  AWA "))
        assertFalse(env.clients.clientNameExists("awa", exceptId = "c1"))
        assertFalse(env.clients.addClient(awa.copy(id = "c2", name = "  ")))

        assertTrue(env.clients.updateClient(awa.copy(phone = "07 11")))
        assertEquals("07 11", env.clients.getClientById("c1")!!.phone)
        assertFalse(env.clients.updateClient(awa.copy(id = "inconnu")))

        assertTrue(env.clients.deleteClient("c1"))
        assertFalse(env.clients.deleteClient("c1"))
        assertNull(env.clients.getClientById("c1"))
    }

    @Test
    fun detteDUnClientParIdentifiantOuParNom() {
        val env = LedgerEnv()
        val awa = Client("c1", "Awa Koné", "", "2026-10-01T10:00:00.000Z")

        env.sales.saveSales(
            listOf(
                ledgerSale("sA", 1000.0, remaining = 1000.0, createdAt = "2026-10-01T10:00:00.000Z", customerId = "c1"),
                ledgerSale("sB", 500.0, remaining = 500.0, createdAt = "2026-10-05T10:00:00.000Z", borrower = "  awa koné "),
                ledgerSale("sC", 300.0, remaining = 300.0, customerId = "autre"),
                ledgerSale("sD", 400.0, remaining = 400.0, customerId = "c1", cancelledAt = "2026-10-06T10:00:00.000Z"),
                ledgerSale("sE", 700.0, remaining = 0.0, customerId = "c1")
            )
        )
        env.credits.recordCreditPayment(listOf("sA"), 200.0, PaymentMethod.Cash)

        val debt = env.clients.getClientDebt(awa)
        assertEquals(1300.0, debt.amount, 0.0)
        assertEquals(listOf("sB", "sA"), debt.sales.map { it.id })
    }

    @Test
    fun regularisationsDUnClientRecentesNonAnnulees() {
        val env = LedgerEnv()
        val awa = Client("c1", "Awa", "", "2026-10-01T10:00:00.000Z")
        env.sales.saveSales(listOf(ledgerSale("sA", 3000.0, remaining = 3000.0, customerId = "c1")))

        env.credits.recordCreditPayment(listOf("sA"), 100.0, PaymentMethod.Cash)
        env.credits.recordCreditPayment(listOf("sA"), 200.0, PaymentMethod.Cash)
        env.credits.recordCreditPayment(listOf("sA"), 300.0, PaymentMethod.Cash)

        assertEquals(3, env.clients.getClientPayments(awa).size)
        assertEquals(2, env.clients.getClientPayments(awa, limit = 2).size)

        val first = env.credits.getCreditPayments().first()
        env.credits.cancelCreditPayment(first.id)
        assertEquals(2, env.clients.getClientPayments(awa).size)
    }

    // ---- Dépenses ----

    @Test
    fun depensesCategorieInconnueEcarteeEtTotaux() {
        val env = LedgerEnv()
        env.store.set(
            "ejden_expenses",
            """[{"id":"e0","label":"X","amount":50,"category":"inconnue","createdAt":"2026-10-09T08:00:00.000Z"}]"""
        )
        assertTrue(env.expenses.getExpenses().isEmpty())
        assertNotNull(env.store.get("ejden_expenses_backup"))

        env.expenses.saveAll(
            listOf(
                Expense("e1", "Transport", 100.0, ExpenseCategory.Transport, "2026-10-09T08:00:00.000Z"),
                Expense("e2", "Loyer", 300.0, ExpenseCategory.Rent, "2026-10-02T08:00:00.000Z"),
                Expense("e3", "Ancien", 999.0, ExpenseCategory.Other, "2026-09-30T08:00:00.000Z")
            )
        )
        val totals = env.expenses.getExpensesTotals(LEDGER_NOW, ZoneOffset.UTC)
        assertEquals(100.0, totals.today, 0.0)
        assertEquals(400.0, totals.month, 0.0)

        assertFalse(env.expenses.addExpense(Expense("e4", "Rien", 0.0, ExpenseCategory.Other, "2026-10-09T08:00:00.000Z")))
        assertTrue(env.expenses.deleteExpense("e1"))
        assertFalse(env.expenses.deleteExpense("e1"))
    }

    // ---- Caisse ----

    @Test
    fun soldeEtMouvementsDeLaCaisse() {
        val env = LedgerEnv()
        env.sales.saveSales(
            listOf(
                ledgerSale("sale-abcdef", 1000.0),
                ledgerSale("s2", 2000.0, remaining = 1500.0, createdAt = "2026-10-08T09:00:00.000Z", method = PaymentMethod.Credit, borrower = "Awa"),
                ledgerSale("s3", 999.0, cancelledAt = "2026-10-09T09:30:00.000Z")
            )
        )
        env.credits.recordCreditPayment(listOf("s2"), 300.0, PaymentMethod.Cash)
        env.cash.addCashEntry(CashEntry("c1", CashType.Out, 200.0, "Achat sacs", "2026-10-09T11:00:00.000Z"))
        env.expenses.addExpense(Expense("e1", "Transport", 100.0, ExpenseCategory.Transport, "2026-10-09T10:30:00.000Z"))

        val summary = env.cash.getCashSummary(LEDGER_NOW, ZoneOffset.UTC)

        assertEquals(1500.0, summary.balance, 0.0)
        assertEquals(1300.0, summary.todayIn, 0.0)
        assertEquals(300.0, summary.todayOut, 0.0)
        assertEquals(1300.0, summary.salesByMethod[PaymentMethod.Cash]!!, 0.0)
        assertEquals(500.0, summary.salesByMethod[PaymentMethod.Credit]!!, 0.0)

        assertEquals("cash-c1", summary.movements.first().id)
        assertEquals("c1", summary.movements.first().deletableId)
        assertTrue(summary.movements.any { it.label == "Vente N° ABCDEF" })
        assertTrue(summary.movements.any { it.label == "Remboursement · Awa" })
        assertTrue(summary.movements.none { it.id == "sale-s3" })
    }

    @Test
    fun mouvementsSupplementairesEtEntreesManuelles() {
        val env = LedgerEnv()
        val extra = CashMovement(
            "invoice-payment-1", CashSource.InvoicePayment, 700.0, "Facture F-1",
            "2026-10-09T09:00:00.000Z", PaymentMethod.MobileMoney
        )
        val cash = CashRepository(env.store, env.sales, env.credits, env.expenses) { listOf(extra) }

        assertTrue(cash.addCashEntry(CashEntry("c1", CashType.In, 250.0, "Apport", "2026-10-09T08:00:00.000Z")))
        assertFalse(cash.addCashEntry(CashEntry("c2", CashType.In, 0.0, "Rien", "2026-10-09T08:00:00.000Z")))

        val summary = cash.getCashSummary(LEDGER_NOW, ZoneOffset.UTC)
        assertEquals(950.0, summary.balance, 0.0)
        assertEquals(700.0, summary.salesByMethod[PaymentMethod.MobileMoney]!!, 0.0)

        assertTrue(cash.deleteCashEntry("c1"))
        assertFalse(cash.deleteCashEntry("c1"))
    }

    // ---- Résumés du tableau de bord ----

    @Test
    fun creancesEtStock() {
        val env = LedgerEnv()
        env.sales.saveSales(
            listOf(
                ledgerSale("s1", 1000.0, remaining = 1000.0, customerId = "c1"),
                ledgerSale("s2", 500.0, remaining = 500.0, customerId = "c1"),
                ledgerSale("s3", 300.0, remaining = 300.0, borrower = "Bob"),
                ledgerSale("s4", 400.0, remaining = 400.0, cancelledAt = "2026-10-09T09:30:00.000Z")
            )
        )
        val receivables = getReceivables(env.sales, env.credits)
        assertEquals(1800.0, receivables.amount, 0.0)
        assertEquals(2, receivables.debtorsCount)

        env.products.saveProducts(
            listOf(
                Product("a", "Riz", 1.0, 2.0, 0, 5, null, null, null),
                Product("b", "Sucre", 1.0, 2.0, 3, 5, null, null, null),
                Product("c", "Sel", 1.0, 2.0, 50, 5, null, null, null),
                Product("d", "Thé", 1.0, 2.0, 2, 0, null, null, null)
            )
        )
        val stock = getStockSummary(env.products)
        assertEquals(4, stock.productsCount)
        assertEquals(1, stock.lowCount)
        assertEquals(1, stock.outCount)
        assertEquals(listOf("a", "b"), stock.watchList.map { it.id })
    }
}
