package com.ejden.app.data

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

private const val CLIENTS_DAY_MS = 86_400_000L

data class BasketStats(
    val mean: Double,
    val median: Double,
    val itemsPerSale: Double,
    val maxSale: Double,
    val creditSharePct: Double
)

data class PairStat(val a: String, val b: String, val count: Int, val sharePct: Double)
data class PairsStats(val top: List<PairStat>, val multiSharePct: Double)
data class ClientTop(val name: String, val amount: Double, val count: Int)
data class ClientSleeping(val name: String, val daysSince: Int, val count: Int)

data class ClientsIdentified(
    val active: Int,
    val fresh: Int,
    val returning: Int,
    val repeatPct: Double,
    val identifiedPct: Double,
    val top: List<ClientTop>,
    val topSharePct: Double,
    val sleeping: List<ClientSleeping>
)

data class ClientStats(
    val baskets: BasketStats,
    val pairs: PairsStats,
    val identified: ClientsIdentified?
)

private class ClientAcc(val name: String, var first: Long) {
    var last: Long = first
    var amount = 0.0
    var count = 0
    var inPeriod = 0
}

private class PairAcc(val a: String, val b: String) {
    var count = 0
}

/** Retourne null s'il n'y a aucune vente sur la période (message vide du HTML). */
fun computeClients(
    rows: List<StatRow>,
    clients: List<Client>,
    horizonDays: Int,
    nowMillis: Long
): ClientStats? {
    val start = nowMillis - horizonDays * CLIENTS_DAY_MS
    val period = rows.filter { it.at >= start }
    if (period.isEmpty()) return null

    /* Paniers */
    val totals = period.map { it.sale.total }.sorted()
    val sumTotals = totals.sum()
    val baskets = BasketStats(
        mean = sumTotals / totals.size,
        median = totals[totals.size / 2],
        itemsPerSale = period.sumOf { r -> r.sale.items.sumOf { it.quantity } } / period.size,
        maxSale = totals.last(),
        creditSharePct = period
            .filter { it.sale.paymentMethod == PaymentMethod.Credit }
            .sumOf { it.sale.total } / sumTotals * 100
    )

    /* Produits achetés ensemble */
    val pairs = LinkedHashMap<String, PairAcc>()
    var multi = 0
    for (row in period) {
        val names = LinkedHashMap<String, String>()
        for (item in row.sale.items) names[item.productId] = item.productName
        val list = names.entries.take(15)
        if (list.size < 2) continue
        multi += 1
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val (x, y) = if (list[i].key < list[j].key) list[i] to list[j] else list[j] to list[i]
                pairs.getOrPut("${x.key}|${y.key}") { PairAcc(x.value, y.value) }.count += 1
            }
        }
    }
    val topPairs = pairs.values
        .filter { it.count >= 2 }
        .sortedByDescending { it.count }
        .take(5)
        .map { PairStat(it.a, it.b, it.count, it.count.toDouble() / period.size * 100) }
    val pairsStats = PairsStats(topPairs, multi.toDouble() / period.size * 100)

    /* Clients identifiés */
    val names = clients.associate { it.id to it.name }
    fun isKnown(row: StatRow): Boolean {
        val id = row.sale.customerId
        return id != null && names.containsKey(id)
    }

    val perClient = LinkedHashMap<String, ClientAcc>()
    for (row in rows.filter { isKnown(it) }) {
        val id = row.sale.customerId ?: ""
        val e = perClient.getOrPut(id) { ClientAcc(names[id] ?: "Client", row.at) }
        e.first = min(e.first, row.at)
        e.last = max(e.last, row.at)
        if (row.at >= start) {
            e.amount += row.sale.total
            e.count += 1
            e.inPeriod += 1
        }
    }

    val active = perClient.values.filter { it.inPeriod > 0 }
    if (active.isEmpty()) return ClientStats(baskets, pairsStats, null)

    val identifiedCount = period.count { isKnown(it) }
    val fresh = active.count { it.first >= start }
    val repeat = active.count { it.count >= 2 }
    val cutoff60 = nowMillis - 60 * CLIENTS_DAY_MS
    val sleeping = perClient.values
        .filter { it.last < cutoff60 && it.count >= 2 && it.first < cutoff60 }
        .sortedByDescending { it.amount }
        .take(5)
        .map {
            ClientSleeping(
                it.name,
                floor((nowMillis - it.last).toDouble() / CLIENTS_DAY_MS).toInt(),
                it.count
            )
        }
    val topClients = active.sortedByDescending { it.amount }.take(5)
    val topShare = topClients.sumOf { it.amount }

    return ClientStats(
        baskets,
        pairsStats,
        ClientsIdentified(
            active = active.size,
            fresh = fresh,
            returning = active.size - fresh,
            repeatPct = repeat.toDouble() / active.size * 100,
            identifiedPct = identifiedCount.toDouble() / period.size * 100,
            top = topClients.map { ClientTop(it.name, it.amount, it.count) },
            topSharePct = topShare / max(1.0, active.sumOf { it.amount }) * 100,
            sleeping = sleeping
        )
    )
}
