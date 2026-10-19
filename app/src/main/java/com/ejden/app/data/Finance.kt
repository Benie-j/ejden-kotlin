package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant

data class MethodTotal(val count: Int, val amount: Double)

data class ProductMargin(val name: String, val quantity: Double, val margin: Double)

data class FinanceFigures(
    val salesCount: Int,
    val revenue: Double,
    /** Marge brute, calculée sur les ventes dont le prix d'achat est connu. null si aucune. */
    val margin: Double?,
    /** Chiffre d'affaires de ces ventes (pour le taux de marge). */
    val costedRevenue: Double,
    /** Charges d'exploitation : dépenses hors « Marchandises ». */
    val charges: Double,
    val goodsExpenses: Double,
    val expensesByCategory: Map<ExpenseCategory, Double>,
    val byMethod: Map<PaymentMethod, MethodTotal>,
    /** Les 5 produits les plus rentables (marge positive). */
    val products: List<ProductMargin>
) {
    /** Résultat net estimé : marge brute − charges ; null si la marge est inconnue. */
    fun netResult(): Double? = margin?.let { it - charges }
}

/** Chiffres de la période [fromMillis, toMillis] (bornes incluses). */
fun computeFinanceFigures(
    sales: List<Sale>,
    expenses: List<Expense>,
    fromMillis: Long,
    toMillis: Long
): FinanceFigures {
    var salesCount = 0
    var revenue = 0.0
    var margin = 0.0
    var marginKnown = false
    var costedRevenue = 0.0
    val byMethod = LinkedHashMap<PaymentMethod, MethodTotal>()
    val productMap = LinkedHashMap<String, ProductMargin>()

    for (sale in sales) {
        val at = parseDateMillis(sale.createdAt)
        if (sale.isCancelled || at == null || at < fromMillis || at > toMillis) continue

        salesCount += 1
        revenue += sale.total

        val method = byMethod[sale.paymentMethod] ?: MethodTotal(0, 0.0)
        byMethod[sale.paymentMethod] = MethodTotal(method.count + 1, method.amount + sale.total)

        // Marge : seulement si tous les articles ont un prix d'achat connu.
        if (!sale.items.all { it.unitCost != null }) continue

        var saleMargin = 0.0
        for (item in sale.items) {
            val itemMargin = (item.unitPrice - (item.unitCost ?: 0.0)) * item.quantity
            saleMargin += itemMargin

            val entry = productMap[item.productId] ?: ProductMargin(item.productName, 0.0, 0.0)
            productMap[item.productId] = ProductMargin(
                entry.name,
                entry.quantity + item.quantity,
                entry.margin + itemMargin
            )
        }

        margin += saleMargin - sale.discount
        marginKnown = true
        costedRevenue += sale.total
    }

    var charges = 0.0
    var goodsExpenses = 0.0
    val byCategory = LinkedHashMap<ExpenseCategory, Double>()

    for (expense in expenses) {
        val at = parseDateMillis(expense.createdAt)
        if (at == null || at < fromMillis || at > toMillis) continue

        byCategory[expense.category] = (byCategory[expense.category] ?: 0.0) + expense.amount
        if (expense.category == ExpenseCategory.Merchandise) {
            goodsExpenses += expense.amount
        } else {
            charges += expense.amount
        }
    }

    val products = productMap.values
        .filter { it.margin > 0.0 }
        .sortedByDescending { it.margin }
        .take(5)

    return FinanceFigures(
        salesCount = salesCount,
        revenue = revenue,
        margin = if (marginKnown) margin else null,
        costedRevenue = costedRevenue,
        charges = charges,
        goodsExpenses = goodsExpenses,
        expensesByCategory = byCategory,
        byMethod = byMethod,
        products = products
    )
}

data class FinanceAlert(val text: String, val danger: Boolean)

private fun plural(n: Int, word: String): String = if (n > 1) word + "s" else word

/** « Points d'attention » : dans l'ordre d'affichage. */
fun computeFinanceAlerts(
    figures: FinanceFigures,
    credits: CreditsSummary,
    stock: StockSummary,
    supplierDebt: Double,
    cashBalance: Double
): List<FinanceAlert> {
    val alerts = ArrayList<FinanceAlert>()
    val result = figures.netResult()

    if (result != null && result < 0.0) {
        alerts.add(
            FinanceAlert("Les charges dépassent la marge de " + formatMoney(-result) + " sur cette période.", true)
        )
    }

    if (cashBalance < 0.0) {
        alerts.add(
            FinanceAlert(
                "La caisse est négative (" + formatMoney(cashBalance) + ") : vérifiez les entrées et sorties.",
                true
            )
        )
    }

    if (credits.overdueAmount > 0.0) {
        alerts.add(
            FinanceAlert(
                formatMoney(credits.overdueAmount) + " de crédits clients sont en retard (" +
                    credits.overdueDebtors + " " + plural(credits.overdueDebtors, "client") + ").",
                false
            )
        )
    }

    if (supplierDebt > 0.0 && supplierDebt > cashBalance) {
        alerts.add(
            FinanceAlert(
                "Vous devez " + formatMoney(supplierDebt) + " aux fournisseurs, plus que le solde de la caisse.",
                false
            )
        )
    }

    if (stock.outCount > 0 || stock.lowCount > 0) {
        alerts.add(
            FinanceAlert(
                "Stock : " + stock.outCount + " " + plural(stock.outCount, "produit") + " en rupture, " +
                    stock.lowCount + " " + plural(stock.lowCount, "bientôt épuisé") + ".",
                false
            )
        )
    }

    return alerts
}

/** « Situation actuelle » : ce que le commerce possède et doit, aujourd'hui. */
data class FinancePosition(
    val cash: Double,
    val credits: Double,
    val creditDebtors: Int,
    val invoices: Double,
    val invoicesCount: Int,
    val stockCost: Double,
    val stockRetail: Double,
    val supplierDebt: Double
) {
    /** Caisse + crédits et factures à encaisser + stock au prix d'achat − dettes fournisseurs. */
    val equity: Double get() = cash + credits + invoices + stockCost - supplierDebt
}

fun computeFinancePosition(
    cashBalance: Double,
    credits: CreditsSummary,
    receivable: InvoiceReceivable,
    stock: StockValue,
    supplierDebt: Double
): FinancePosition = FinancePosition(
    cash = cashBalance,
    credits = credits.total,
    creditDebtors = credits.debtors,
    invoices = receivable.total,
    invoicesCount = receivable.count,
    stockCost = stock.cost,
    stockRetail = stock.retail,
    supplierDebt = supplierDebt
)

/** Période choisie dans Finances (mémorisée à part de celle du tableau de bord ; défaut : 1 mois). */
class FinancePeriodRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_finances_period"
    }

    fun get(): PeriodChoice {
        val fallback = PeriodChoice(PeriodPreset.Month1)
        val stored = store.get(KEY)
        if (stored == null || stored.isEmpty()) return fallback

        return try {
            val parsed = JSONTokener(stored).nextValue() as? JSONObject ?: return fallback
            val preset = PeriodPreset.fromId(parsed.opt("preset")) ?: return fallback

            if (preset == PeriodPreset.Custom) {
                val from = parsed.opt("from")
                val to = parsed.opt("to")
                if (isValidDay(from) && isValidDay(to) && (from as String) <= (to as String)) {
                    PeriodChoice(PeriodPreset.Custom, from, to)
                } else {
                    fallback
                }
            } else {
                PeriodChoice(preset)
            }
        } catch (e: Exception) {
            fallback
        }
    }

    fun save(choice: PeriodChoice): Boolean {
        val json = JSONObject()
            .put("preset", choice.preset.id)
            .put("from", choice.from ?: JSONObject.NULL)
            .put("to", choice.to ?: JSONObject.NULL)
        return store.set(KEY, json.toString())
    }
}

/** Tout ce que l'écran Finances affiche pour une période. */
data class FinanceReport(
    val current: FinanceFigures,
    val previous: FinanceFigures,
    val alerts: List<FinanceAlert>,
    val cashFlow: CashFlow,
    val collected: InvoiceCollected,
    val receivable: InvoiceReceivable,
    val position: FinancePosition,
    /** « hier » ou « période précédente ». */
    val comparedTo: String
)

class FinanceRepository(
    private val sales: SaleRepository,
    private val expenses: ExpenseRepository,
    private val cash: CashRepository,
    private val credits: CreditPaymentRepository,
    private val clients: ClientRepository,
    private val products: ProductRepository,
    private val supplierPayments: SupplierPaymentRepository,
    private val invoices: InvoiceRepository
) {
    fun load(period: ResolvedPeriod): FinanceReport {
        val allSales = sales.getSales()
        val allExpenses = expenses.getExpenses()

        val current = computeFinanceFigures(allSales, allExpenses, period.startMillis, period.endMillis)
        val previous = computeFinanceFigures(
            allSales, allExpenses, period.previousStartMillis, period.previousEndMillis
        )

        val cashSummary = cash.getCashSummary()
        val creditsSummary = computeCreditsSummary(allSales, credits.getCreditPayments(), clients.getClients())
        val stockSummary = getStockSummary(products, 0)
        val stockValue = getStockValue(products.getProducts())
        val debt = supplierPayments.getSuppliersDebtTotal()
        val receivable = invoices.receivable()

        return FinanceReport(
            current = current,
            previous = previous,
            alerts = computeFinanceAlerts(current, creditsSummary, stockSummary, debt, cashSummary.balance),
            cashFlow = cashSummary.flowBetween(period.startMillis, period.endMillis),
            collected = invoices.collectedBetween(
                Instant.ofEpochMilli(period.startMillis),
                Instant.ofEpochMilli(period.endMillis)
            ),
            receivable = receivable,
            position = computeFinancePosition(cashSummary.balance, creditsSummary, receivable, stockValue, debt),
            comparedTo = period.comparedTo
        )
    }
}
