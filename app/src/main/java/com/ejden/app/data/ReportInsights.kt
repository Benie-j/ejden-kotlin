package com.ejden.app.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

private const val REPORT_INSIGHT_DAY_MS = 86_400_000.0

enum class ReportInsightLevel(val id: String) {
    Info("info"),
    Positive("positif"),
    Warning("attention"),
    Alert("alerte")
}

data class ReportInsight(
    val id: String,
    val type: String,
    val level: ReportInsightLevel,
    val title: String,
    val explanation: String,
    val period: String,
    val data: String,
    /** null quand le niveau de confiance n'a pas de sens (état actuel, crédits…). */
    val confidence: ReportConfidence?
)

private fun insightMoney(value: Double): String = statNumber(Math.round(value).toDouble())

/**
 * Messages automatiques du rapport. Aucun message sans donnée : sans vente sur la période,
 * la liste est vide.
 */
fun buildReportInsights(
    range: ReportRange,
    historyDays: Int,
    totals: ReportTotals,
    previous: ReportTotals?,
    analyses: ReportAnalyses,
    stock: ReportStock,
    payments: ReportPayments,
    clients: ReportClients,
    nowMillis: Long
): List<ReportInsight> {
    val out = ArrayList<ReportInsight>()
    val per = reportRangeLabel(range)

    fun add(
        type: String,
        level: ReportInsightLevel,
        title: String,
        explanation: String,
        period: String,
        data: String,
        confidence: ReportConfidence?
    ) {
        out.add(ReportInsight("i${out.size + 1}", type, level, title, explanation, period, data, confidence))
    }

    if (totals.count == 0) return out

    // Ventes comparées à la période de référence
    if (previous != null && previous.revenue > 0) {
        val change = ReportStats.percentChange(totals.revenue, previous.revenue)
        if (change != null && abs(change) >= 5) {
            val percent = ReportStats.formatPercent(change, false)
            add(
                "Ventes",
                if (change > 0) ReportInsightLevel.Positive else ReportInsightLevel.Warning,
                if (change > 0) "Vos ventes progressent de $percent" else "Vos ventes reculent de $percent",
                "Chiffre d'affaires comparé à la période de référence.",
                per,
                "${totals.count} ventes, ${previous.count} ventes sur la période comparée",
                ReportStats.confidenceFromSample(previous.count, 10, 40)
            )
        }
    }

    // Tendance
    val trend = analyses.trend
    if (trend is ReportAnalysis.Ready && trend.value.direction != ReportDirection.Flat) {
        val t = trend.value
        val acceleration = t.acceleration
        val accelerationText =
            if (acceleration != null && acceleration != ReportAcceleration.Flat) ", ${acceleration.id}" else ""
        val rising = t.direction == ReportDirection.Rise
        val relative = ReportStats.formatPercent(t.relative)
        val r2 = String.format(Locale.ROOT, "%.2f", t.r2)
        add(
            "Tendance",
            if (rising) ReportInsightLevel.Positive else ReportInsightLevel.Warning,
            if (rising) "Tendance des ventes à la hausse" else "Tendance des ventes à la baisse",
            "Pente estimée sur $historyDays jours ($relative sur l'historique$accelerationText).",
            "$historyDays derniers jours",
            "Régression linéaire, R² = $r2",
            t.confidence
        )
    }

    // Anomalies (les 2 premières)
    val anomalies = analyses.anomalies
    if (anomalies is ReportAnalysis.Ready) {
        val info = anomalies.value
        for (item in info.items.take(2)) {
            val z = String.format(Locale.ROOT, "%.1f", item.z)
            add(
                "Anomalie",
                ReportInsightLevel.Warning,
                "Activité exceptionnellement ${item.kind.id} le ${item.day}",
                "${insightMoney(item.value)} FCFA, alors que l'activité habituelle se situe entre " +
                    "${insightMoney(info.low)} et ${insightMoney(info.high)} FCFA.",
                item.day,
                "Z-score = $z",
                info.confidence
            )
        }
    }

    // Stock qui pourrait manquer dans la semaine (les 3 plus urgents)
    val soon = stock.rows
        .mapNotNull { row ->
            val left = row.daysLeft
            if (left != null && left <= 7 && row.stock > 0) row to left else null
        }
        .sortedBy { it.second }
        .take(3)
    for ((row, left) in soon) {
        add(
            "Stock",
            if (left <= 3) ReportInsightLevel.Alert else ReportInsightLevel.Warning,
            "Le stock de « ${row.name} » pourrait être insuffisant dans environ " +
                "${max(1L, Math.round(left))} jour(s)",
            "Stock ${row.stock}, vente moyenne ${statNumber(row.dailyRate ?: 0.0, 1)}/jour.",
            per,
            "Rythme de vente observé sur la période",
            ReportStats.confidenceFromSample(historyDays, 7, 30)
        )
    }

    val outOfStock = stock.rows.count { it.status == ReportStockStatus.Out }
    if (outOfStock > 0) {
        add(
            "Stock",
            ReportInsightLevel.Alert,
            "$outOfStock produit(s) en rupture de stock",
            "Stock à zéro actuellement.",
            "Aujourd'hui",
            "Stock actuel",
            null
        )
    }

    // Crédits
    val previousShare = payments.prevCreditShare
    if (previousShare != null && abs(payments.creditShare - previousShare) >= 5) {
        add(
            "Crédits",
            if (payments.creditShare > previousShare) ReportInsightLevel.Warning else ReportInsightLevel.Positive,
            "Les ventes à crédit représentent ${ReportStats.formatPercent(payments.creditShare, false)} " +
                "de vos ventes, contre ${ReportStats.formatPercent(previousShare, false)} sur la période précédente",
            "Part du chiffre d'affaires non encaissée à la vente.",
            per,
            "${payments.creditSales} vente(s) à crédit",
            null
        )
    }

    if (payments.overdueCount > 0) {
        add(
            "Crédits",
            ReportInsightLevel.Alert,
            "${payments.overdueCount} crédit(s) en retard",
            "${insightMoney(payments.overdue)} FCFA restent à encaisser depuis plus de $CREDIT_OVERDUE_DAYS jours.",
            per,
            "Ventes à crédit non soldées",
            null
        )
    }

    // Clients plus silencieux que d'habitude
    val late = clients.rows.count { client ->
        val interval = client.intervalDays
        val last = client.lastAt
        interval != null && last != null &&
            (nowMillis - last) / REPORT_INSIGHT_DAY_MS > interval * 2 &&
            client.count == 0
    }
    if (late > 0) {
        add(
            "Clients",
            ReportInsightLevel.Info,
            "$late client(s) n'ont effectué aucun achat depuis plus de 2 fois leur fréquence habituelle",
            "Fréquence calculée sur leurs achats passés (au moins 3 achats).",
            per,
            "Intervalle médian entre achats",
            ReportConfidence.Medium
        )
    }

    return out
}
