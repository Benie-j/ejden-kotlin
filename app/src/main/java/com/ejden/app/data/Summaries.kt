package com.ejden.app.data

data class ReceivablesSummary(val amount: Double, val debtorsCount: Int)

/** Total encore dû par les clients (ventes à crédit non soldées). */
fun getReceivables(sales: SaleRepository, credits: CreditPaymentRepository): ReceivablesSummary {
    val all = sales.getSales()
    val remaining = computeRemainingMap(all, credits.getCreditPayments())
    val debtors = HashSet<String>()
    var amount = 0.0

    for (sale in all) {
        val due = remaining[sale.id] ?: 0.0
        if (due > 0.0) {
            amount += due
            debtors.add(sale.customerId ?: sale.borrowerName ?: sale.id)
        }
    }
    return ReceivablesSummary(amount, debtors.size)
}

data class StockSummary(
    val productsCount: Int,
    val lowCount: Int,
    val outCount: Int,
    val watchList: List<Product>
)

fun getStockSummary(products: ProductRepository, watchLimit: Int = 4): StockSummary {
    val all = products.getProducts()
    fun isOut(p: Product) = p.stock <= 0
    fun isLow(p: Product) = p.stock > 0 && p.stockThreshold > 0 && p.stock <= p.stockThreshold

    val watch = all
        .filter { isOut(it) || isLow(it) }
        .sortedBy { it.stock }
        .take(maxOf(0, watchLimit))

    return StockSummary(all.size, all.count { isLow(it) }, all.count { isOut(it) }, watch)
}
