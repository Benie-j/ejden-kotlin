package com.ejden.app.data

import java.time.ZoneId
import kotlin.math.max

private const val REPORT_PAY_DAY_MS = 86_400_000.0

data class ReportMethodRow(
    val method: PaymentMethod,
    val label: String,
    val count: Int,
    val amount: Double,
    val share: Double
)

data class ReportCreditRow(
    val date: Long,
    val client: String,
    val amount: Double,
    /** Reste réel à encaisser (après remboursements). */
    val remaining: Double
)

data class ReportPayments(
    val methods: List<ReportMethodRow>,
    val collected: Double,
    val toReceive: Double,
    val creditSales: Int,
    val creditSalesAmount: Double,
    val repaid: Double,
    val overdue: Double,
    val overdueCount: Int,
    val creditList: List<ReportCreditRow>,
    val creditShare: Double,
    /** null si la comparaison est indisponible. */
    val prevCreditShare: Double?
)

/** remaining = reste à payer de chaque vente (computeRemainingMap). */
fun computeReportPayments(
    views: List<ReportSaleView>,
    totals: ReportTotals,
    comparison: ReportComparison,
    clients: List<Client>,
    creditPayments: List<CreditPayment>,
    remaining: Map<String, Double>,
    filters: ReportFilters,
    range: ReportRange,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): ReportPayments {
    val okViews = views.filter { !it.cancelled }
    fun left(view: ReportSaleView): Double = remaining[view.sale.id] ?: 0.0

    val methodCount = LinkedHashMap<PaymentMethod, Int>()
    val methodAmount = LinkedHashMap<PaymentMethod, Double>()
    for (view in okViews) {
        val method = view.sale.paymentMethod
        methodCount[method] = (methodCount[method] ?: 0) + 1
        methodAmount[method] = (methodAmount[method] ?: 0.0) + view.amount
    }

    val creditViews = okViews.filter { it.sale.remaining > 0 }
    val saleIds = okViews.map { it.sale.id }.toSet()
    val toReceive = okViews.sumOf { left(it) }
    val overdue = okViews.filter {
        left(it) > 0 && (nowMillis - it.at) / REPORT_PAY_DAY_MS > CREDIT_OVERDUE_DAYS
    }

    val noItemFilter = filters.productId == "" && filters.category == "" && filters.clientId == ""
    val repaid = creditPayments
        .filter { !it.isCancelled }
        .filter { p ->
            if (saleIds.contains(p.saleId)) {
                true
            } else {
                val at = parseDateMillis(p.createdAt)
                at != null && range.contains(at, zone) && noItemFilter
            }
        }
        .sumOf { it.amount }

    val creditShare =
        if (totals.revenue > 0) creditViews.sumOf { it.amount } / totals.revenue * 100 else 0.0
    val previous = comparison.previous
    val prevCreditShare = if (previous != null && previous.revenue > 0) {
        comparison.views
            .filter { !it.cancelled && it.sale.remaining > 0 }
            .sumOf { it.amount } / previous.revenue * 100
    } else null

    val methods = methodAmount.entries
        .sortedByDescending { it.value }
        .map { (method, amount) ->
            ReportMethodRow(
                method = method,
                label = reportMethodLabel(method),
                count = methodCount[method] ?: 0,
                amount = amount,
                share = if (totals.revenue > 0) amount / totals.revenue * 100 else 0.0
            )
        }

    val creditList = creditViews
        .map { view ->
            ReportCreditRow(
                date = view.at,
                client = view.sale.customerId
                    ?.let { id -> clients.firstOrNull { it.id == id }?.name }
                    ?: view.sale.borrowerName
                    ?: "Client de passage",
                amount = view.amount,
                remaining = left(view)
            )
        }
        .sortedByDescending { it.date }

    return ReportPayments(
        methods = methods,
        collected = max(0.0, totals.revenue - toReceive),
        toReceive = toReceive,
        creditSales = creditViews.size,
        creditSalesAmount = creditViews.sumOf { it.amount },
        repaid = repaid,
        overdue = overdue.sumOf { left(it) },
        overdueCount = overdue.size,
        creditList = creditList,
        creditShare = creditShare,
        prevCreditShare = prevCreditShare
    )
}
