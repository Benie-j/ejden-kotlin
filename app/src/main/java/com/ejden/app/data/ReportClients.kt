package com.ejden.app.data

import java.time.ZoneId
import kotlin.math.max

private const val REPORT_CLIENT_DAY_MS = 86_400_000.0

enum class ReportClientStatus(val id: String) {
    Active("actif"),
    Inactive("inactif")
}

data class ReportClientRow(
    val id: String,
    val name: String,
    /** Achats non annulés sur la période (filtres appliqués). */
    val count: Int,
    val amount: Double,
    val average: Double,
    /** Dernier achat connu, sur tout l'historique. */
    val lastAt: Long?,
    /** Intervalle médian entre achats ; null sous 3 achats. */
    val intervalDays: Double?,
    val isNew: Boolean,
    val status: ReportClientStatus
)

data class ReportClients(
    val rows: List<ReportClientRow>,
    val total: Int,
    val newCount: Int,
    val active: Int,
    val recurring: Int,
    val inactive: Int,
    val avgAmount: Double
)

fun computeReportClients(
    views: List<ReportSaleView>,
    sales: List<Sale>,
    clients: List<Client>,
    filters: ReportFilters,
    range: ReportRange,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): ReportClients {
    val okViews = views.filter { !it.cancelled }

    // Historique complet (sans filtre) : sert à l'intervalle entre achats et à l'inactivité.
    val history = HashMap<String, MutableList<Long>>()
    for (sale in sales) {
        val customer = sale.customerId
        if (sale.isCancelled || customer.isNullOrEmpty()) continue
        val at = parseDateMillis(sale.createdAt) ?: continue
        history.getOrPut(customer) { ArrayList() }.add(at)
    }

    val rows = ArrayList<ReportClientRow>()
    for (client in clients) {
        if (filters.clientId != "" && client.id != filters.clientId) continue

        val mine = okViews.filter { it.sale.customerId == client.id }
        val times = (history[client.id] ?: emptyList<Long>()).sorted()
        val gaps = times.drop(1).mapIndexed { i, t -> (t - times[i]) / REPORT_CLIENT_DAY_MS }
        val interval = if (gaps.size >= 2) ReportStats.median(gaps) else null
        val last = times.lastOrNull()
        val idle = last?.let { (nowMillis - it) / REPORT_CLIENT_DAY_MS }
        val amount = mine.sumOf { it.amount }
        val createdAt = parseDateMillis(client.createdAt)

        rows.add(
            ReportClientRow(
                id = client.id,
                name = client.name,
                count = mine.size,
                amount = amount,
                average = if (mine.isNotEmpty()) amount / mine.size else 0.0,
                lastAt = last,
                intervalDays = interval,
                isNew = createdAt != null && range.contains(createdAt, zone),
                status = if (idle != null && idle <= max(30.0, (interval ?: 0.0) * 2)) {
                    ReportClientStatus.Active
                } else {
                    ReportClientStatus.Inactive
                }
            )
        )
    }

    val sorted = rows.sortedByDescending { it.amount }
    val buyers = sorted.filter { it.count > 0 }
    return ReportClients(
        rows = sorted,
        total = sorted.size,
        newCount = sorted.count { it.isNew },
        active = buyers.size,
        recurring = buyers.count { it.count >= ReportStats.MIN.clientRecurring },
        inactive = sorted.count { it.count == 0 && it.status == ReportClientStatus.Inactive },
        avgAmount = if (buyers.isNotEmpty()) buyers.sumOf { it.amount } / buyers.size else 0.0
    )
}
