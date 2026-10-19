package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val FROM: Long = Instant.parse("2026-10-01T00:00:00Z").toEpochMilli()
private val TO: Long = Instant.parse("2026-10-31T23:59:59Z").toEpochMilli()

private fun item(id: String, name: String, price: Double, cost: Double?, qty: Double) =
    SaleItem(id, name, price, cost, qty, price * qty)

private fun sale(
    id: String,
    createdAt: String,
    items: List<SaleItem>,
    discount: Double = 0.0,
    method: PaymentMethod = PaymentMethod.Cash,
    cancelled: Boolean = false
): Sale {
    val subtotal = items.sumOf { it.total }
    val total = subtotal - discount
    return Sale(
        id = id,
        createdAt = createdAt,
        items = items,
        subtotal = subtotal,
        discount = discount,
        total = total,
        paymentMethod = method,
        amountPaid = total,
        change = 0.0,
        remaining = 0.0,
        customerId = null,
        borrowerName = null,
        cancelledAt = if (cancelled) "2026-10-08T10:00:00.000Z" else null
    )
}

private fun expense(id: String, amount: Double, category: ExpenseCategory, createdAt: String) =
    Expense(id, "Dépense " + id, amount, category, createdAt)

private fun figures(margin: Double?, charges: Double) = FinanceFigures(
    salesCount = 0, revenue = 0.0, margin = margin, costedRevenue = 0.0, charges = charges,
    goodsExpenses = 0.0, expensesByCategory = emptyMap(), byMethod = emptyMap(), products = emptyList()
)

private val NO_CREDITS = CreditsSummary(0.0, 0, 0.0, 0, emptyList())
private val NO_STOCK = StockSummary(0, 0, 0, emptyList())

class FinanceTest {

    @Test
    fun chiffresDeLaPeriode() {
        val a = item("a", "Produit A", 1000.0, 600.0, 2.0)
        val sales = listOf(
            sale("s1", "2026-10-05T09:00:00.000Z", listOf(a), discount = 100.0),
            sale("s2", "2026-10-06T09:00:00.000Z", listOf(item("b", "Produit B", 500.0, null, 1.0)), method = PaymentMethod.MobileMoney),
            sale("s3", "2026-10-06T10:00:00.000Z", listOf(a), cancelled = true),
            sale("s4", "2026-09-20T09:00:00.000Z", listOf(a)),
            sale("s5", "2026-10-07T09:00:00.000Z", listOf(item("a", "Produit A", 1000.0, 600.0, 1.0)))
        )
        val expenses = listOf(
            expense("e1", 5000.0, ExpenseCategory.Merchandise, "2026-10-03T09:00:00.000Z"),
            expense("e2", 300.0, ExpenseCategory.Transport, "2026-10-04T09:00:00.000Z"),
            expense("e3", 700.0, ExpenseCategory.Rent, "2026-10-05T09:00:00.000Z"),
            expense("e4", 999.0, ExpenseCategory.Transport, "2026-09-30T09:00:00.000Z")
        )

        val f = computeFinanceFigures(sales, expenses, FROM, TO)

        assertEquals(3, f.salesCount)
        assertEquals(3400.0, f.revenue, 0.0)
        assertEquals(1100.0, f.margin!!, 0.0)
        assertEquals(2900.0, f.costedRevenue, 0.0)
        assertEquals(MethodTotal(2, 2900.0), f.byMethod[PaymentMethod.Cash])
        assertEquals(MethodTotal(1, 500.0), f.byMethod[PaymentMethod.MobileMoney])
        assertEquals(1, f.products.size)
        assertEquals("Produit A", f.products[0].name)
        assertEquals(3.0, f.products[0].quantity, 0.0)
        assertEquals(1200.0, f.products[0].margin, 0.0)
        assertEquals(1000.0, f.charges, 0.0)
        assertEquals(5000.0, f.goodsExpenses, 0.0)
        assertEquals(5000.0, f.expensesByCategory[ExpenseCategory.Merchandise]!!, 0.0)
        assertEquals(300.0, f.expensesByCategory[ExpenseCategory.Transport]!!, 0.0)
        assertEquals(700.0, f.expensesByCategory[ExpenseCategory.Rent]!!, 0.0)
        assertEquals(100.0, f.netResult()!!, 0.0)
    }

    @Test
    fun margineInconnueSiAucunPrixDAchat() {
        val sales = listOf(
            sale("s2", "2026-10-06T09:00:00.000Z", listOf(item("b", "Produit B", 500.0, null, 1.0)))
        )
        val f = computeFinanceFigures(sales, emptyList(), FROM, TO)

        assertEquals(1, f.salesCount)
        assertEquals(500.0, f.revenue, 0.0)
        assertNull(f.margin)
        assertNull(f.netResult())
        assertTrue(f.products.isEmpty())
    }

    @Test
    fun produitsLesPlusRentablesCinqMaximumMargePositive() {
        val items = listOf(
            item("p1", "Produit 1", 1000.0, 900.0, 1.0),
            item("p2", "Produit 2", 1000.0, 400.0, 1.0),
            item("p3", "Produit 3", 1000.0, 700.0, 1.0),
            item("p4", "Produit 4", 1000.0, 500.0, 1.0),
            item("p5", "Produit 5", 1000.0, 800.0, 1.0),
            item("p6", "Produit 6", 1000.0, 600.0, 1.0),
            item("p7", "Produit 7", 1000.0, 1050.0, 1.0)
        )
        val f = computeFinanceFigures(
            listOf(sale("s1", "2026-10-05T09:00:00.000Z", items)), emptyList(), FROM, TO
        )

        assertEquals(
            listOf("Produit 2", "Produit 4", "Produit 6", "Produit 3", "Produit 5"),
            f.products.map { it.name }
        )
    }

    @Test
    fun alertesCompletesDansLOrdre() {
        val alerts = computeFinanceAlerts(
            figures = figures(margin = 500.0, charges = 800.0),
            credits = CreditsSummary(2000.0, 3, 1200.0, 2, emptyList()),
            stock = StockSummary(10, 2, 1, emptyList()),
            supplierDebt = 900.0,
            cashBalance = -50.0
        )

        assertEquals(
            listOf(
                FinanceAlert("Les charges dépassent la marge de " + formatMoney(300.0) + " sur cette période.", true),
                FinanceAlert("La caisse est négative (" + formatMoney(-50.0) + ") : vérifiez les entrées et sorties.", true),
                FinanceAlert(formatMoney(1200.0) + " de crédits clients sont en retard (2 clients).", false),
                FinanceAlert("Vous devez " + formatMoney(900.0) + " aux fournisseurs, plus que le solde de la caisse.", false),
                FinanceAlert("Stock : 1 produit en rupture, 2 bientôt épuisés.", false)
            ),
            alerts
        )
    }

    @Test
    fun alerteSeuleEtSituationCalme() {
        val calm = computeFinanceAlerts(figures(500.0, 100.0), NO_CREDITS, NO_STOCK, 0.0, 100.0)
        assertTrue(calm.isEmpty())

        // Une dette fournisseur plus petite que la caisse ne déclenche rien.
        val covered = computeFinanceAlerts(figures(null, 0.0), NO_CREDITS, NO_STOCK, 400.0, 1000.0)
        assertTrue(covered.isEmpty())

        val single = computeFinanceAlerts(
            figures(500.0, 100.0),
            CreditsSummary(100.0, 1, 100.0, 1, emptyList()),
            NO_STOCK, 0.0, 100.0
        )
        assertEquals(
            listOf(FinanceAlert(formatMoney(100.0) + " de crédits clients sont en retard (1 client).", false)),
            single
        )
    }

    @Test
    fun situationNetteEstimee() {
        val position = computeFinancePosition(
            cashBalance = 1000.0,
            credits = CreditsSummary(200.0, 1, 0.0, 0, emptyList()),
            receivable = InvoiceReceivable(300.0, 1, 0.0, 0),
            stock = StockValue(500.0, 800.0),
            supplierDebt = 400.0
        )

        assertEquals(1600.0, position.equity, 0.0)
        assertEquals(200.0, position.credits, 0.0)
        assertEquals(1, position.creditDebtors)
        assertEquals(300.0, position.invoices, 0.0)
        assertEquals(1, position.invoicesCount)
        assertEquals(800.0, position.stockRetail, 0.0)
    }

    @Test
    fun periodeMemoriseeParLeModule() {
        val store = InMemoryStore()
        val repo = FinancePeriodRepository(store)

        assertEquals(PeriodChoice(PeriodPreset.Month1), repo.get())

        assertTrue(repo.save(PeriodChoice(PeriodPreset.Custom, "2026-10-01", "2026-10-31")))
        assertEquals(PeriodChoice(PeriodPreset.Custom, "2026-10-01", "2026-10-31"), repo.get())

        assertTrue(repo.save(PeriodChoice(PeriodPreset.Days7)))
        assertEquals(PeriodChoice(PeriodPreset.Days7), repo.get())

        // La période du tableau de bord est indépendante.
        PeriodRepository(store).save(PeriodChoice(PeriodPreset.Months3))
        assertEquals(PeriodChoice(PeriodPreset.Days7), repo.get())

        // Une période personnalisée à l'envers est ignorée.
        store.set(FinancePeriodRepository.KEY, """{"preset":"custom","from":"2026-10-31","to":"2026-10-01"}""")
        assertEquals(PeriodChoice(PeriodPreset.Month1), repo.get())
    }

    @Test
    fun rapportSurDesDonneesVides() {
        val store = InMemoryStore()
        val products = ProductRepository(store)
        val sales = SaleRepository(store, products)
        val credits = CreditPaymentRepository(store, sales)
        val clients = ClientRepository(store, sales, credits)
        val expenses = ExpenseRepository(store)
        val suppliers = SupplierRepository(store)
        val purchases = PurchaseRepository(store, suppliers, products)
        val supplierPayments = SupplierPaymentRepository(store, purchases)
        val numbering = DocumentNumbering(store)
        val quotes = QuoteRepository(store, numbering)
        val documents = DocumentRepository(store)
        val invoices = InvoiceRepository(store, numbering, quotes, documents)
        val cash = CashRepository(store, sales, credits, expenses, extraMovements = { invoices.cashMovements() })
        val finance = FinanceRepository(sales, expenses, cash, credits, clients, products, supplierPayments, invoices)

        val period = resolvePeriod(PeriodChoice(PeriodPreset.Month1), LocalDate.of(2026, 10, 15), ZoneOffset.UTC)
        val report = finance.load(period)

        assertEquals(0, report.current.salesCount)
        assertNull(report.current.margin)
        assertNull(report.current.netResult())
        assertTrue(report.alerts.isEmpty())
        assertEquals(0.0, report.cashFlow.cashIn, 0.0)
        assertEquals(0, report.collected.count)
        assertEquals(0, report.receivable.count)
        assertEquals(0.0, report.position.equity, 0.0)
        assertEquals("période précédente", report.comparedTo)
    }
}
