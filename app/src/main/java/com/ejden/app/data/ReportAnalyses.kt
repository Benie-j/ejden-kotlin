package com.ejden.app.data

import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private val REPORT_ANALYSIS_WEEKDAYS =
    listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

/** Résultat d'une analyse : disponible, ou indisponible avec la raison (jamais de chiffre inventé). */
sealed interface ReportAnalysis<out T> {
    data class Ready<T>(val value: T) : ReportAnalysis<T>
    data class Unavailable(val reason: String) : ReportAnalysis<Nothing>
}

enum class ReportDirection(val id: String) {
    Rise("hausse"),
    Fall("baisse"),
    Flat("stable")
}

enum class ReportAcceleration(val id: String) {
    Speeding("accélération"),
    Slowing("ralentissement"),
    Flat("stable")
}

enum class ReportAnomalyKind(val id: String) {
    High("élevée"),
    Low("faible")
}

data class ReportTrendResult(
    val slopePerDay: Double,
    /** Variation relative sur tout l'historique, en %. */
    val relative: Double,
    val r2: Double,
    val direction: ReportDirection,
    val acceleration: ReportAcceleration?,
    val ma7: List<Double?>,
    val confidence: ReportConfidence
)

data class ReportVariability(
    val mean: Double,
    val median: Double,
    val std: Double,
    val variance: Double,
    val cv: Double?
)

data class ReportAnomalyItem(val day: String, val value: Double, val z: Double, val kind: ReportAnomalyKind)

data class ReportAnomalies(
    val low: Double,
    val high: Double,
    val items: List<ReportAnomalyItem>,
    val cv: Double?,
    val confidence: ReportConfidence
)

data class ReportWeekdayIndex(val label: String, val index: Double)

data class ReportSeasonality(
    val weekday: List<ReportWeekdayIndex>,
    val confidence: ReportConfidence,
    val monthNote: String
)

data class ReportForecast(
    val nextDays: Int,
    val expectedRevenue: Double,
    val low: Double,
    val high: Double,
    val confidence: ReportConfidence
)

data class ReportAnalyses(
    val trend: ReportAnalysis<ReportTrendResult>,
    val variability: ReportAnalysis<ReportVariability>,
    val anomalies: ReportAnalysis<ReportAnomalies>,
    val seasonality: ReportAnalysis<ReportSeasonality>,
    val forecast: ReportAnalysis<ReportForecast>
)

/**
 * Série quotidienne sur tout l'historique : du premier jour de vente jusqu'à aujourd'hui,
 * filtres produit/catégorie/client/moyen appliqués, ventes annulées exclues du CA.
 */
fun buildReportHistorySeries(
    sales: List<Sale>,
    filters: ReportFilters,
    categoryOf: Map<String, String>,
    historyDays: Int,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): List<ReportDayPoint> {
    val range = ReportRange(today.minusDays((max(historyDays, 1) - 1).toLong()), today)
    val views = buildSaleViews(sales, filters.copy(status = ReportStatus.Active), range, categoryOf, zone)
    return reportDailySeries(views, range, zone)
}

/**
 * allDaily : série de buildReportHistorySeries ; range : période affichée (pour les anomalies).
 */
fun computeReportAnalyses(
    allDaily: List<ReportDayPoint>,
    historyDays: Int,
    range: ReportRange
): ReportAnalyses {
    val series = allDaily.map { it.revenue }
    val activeDays = allDaily.count { it.count > 0 }
    val sd = ReportStats.stdDev(series)

    fun unavailable(days: Int, what: String) = ReportAnalysis.Unavailable(
        "EJDEN ne dispose pas encore de suffisamment d'historique " +
            "($days jours minimum, $historyDays actuellement) pour $what."
    )

    // Tendance (sur l'historique complet, filtres appliqués)
    var trend: ReportAnalysis<ReportTrendResult> =
        unavailable(ReportStats.MIN.trendDays, "déterminer une tendance fiable")
    if (historyDays >= ReportStats.MIN.trendDays && activeDays >= 5) {
        val lt = ReportStats.linearTrend(series)
        val avg = ReportStats.mean(series) ?: 0.0
        if (lt != null) {
            val rel = if (avg > 0) lt.slope * (series.size - 1) / avg * 100 else 0.0
            val half = series.size / 2
            val s1 = ReportStats.linearTrend(series.subList(0, half))
            val s2 = ReportStats.linearTrend(series.subList(half, series.size))
            val acceleration = if (s1 != null && s2 != null) {
                when {
                    s2.slope - s1.slope > abs(avg) * 0.02 -> ReportAcceleration.Speeding
                    s1.slope - s2.slope > abs(avg) * 0.02 -> ReportAcceleration.Slowing
                    else -> ReportAcceleration.Flat
                }
            } else null
            trend = ReportAnalysis.Ready(
                ReportTrendResult(
                    slopePerDay = lt.slope,
                    relative = rel,
                    r2 = lt.r2,
                    direction = when {
                        lt.r2 < 0.1 || abs(rel) < 5 -> ReportDirection.Flat
                        rel > 0 -> ReportDirection.Rise
                        else -> ReportDirection.Fall
                    },
                    acceleration = acceleration,
                    ma7 = ReportStats.movingAverage(series, 7),
                    confidence = ReportStats.confidenceFromSample(historyDays, ReportStats.MIN.trendDays, 60)
                )
            )
        }
    }

    // Variabilité
    var variability: ReportAnalysis<ReportVariability> =
        unavailable(ReportStats.MIN.anomalyDays, "mesurer la variabilité")
    if (historyDays >= ReportStats.MIN.anomalyDays && sd != null) {
        variability = ReportAnalysis.Ready(
            ReportVariability(
                mean = ReportStats.mean(series) ?: 0.0,
                median = ReportStats.median(series) ?: 0.0,
                std = sd,
                variance = sd * sd,
                cv = ReportStats.coefVariation(series)
            )
        )
    }

    // Anomalies : chaque jour de la période est comparé aux autres jours (sans lui-même)
    var anomalies: ReportAnalysis<ReportAnomalies> =
        unavailable(ReportStats.MIN.anomalyDays, "détecter des valeurs inhabituelles")
    if (historyDays >= ReportStats.MIN.anomalyDays && activeDays >= 7 && sd != null && sd > 0) {
        val items = ArrayList<ReportAnomalyItem>()
        for (point in allDaily) {
            if (point.date.isBefore(range.start) || point.date.isAfter(range.end)) continue
            val sample = allDaily.filter { it.day != point.day }.map { it.revenue }
            val z = ReportStats.zScore(point.revenue, sample)
            if (z != null && abs(z) >= 2.5 && (point.revenue > 0 || z < 0)) {
                items.add(
                    ReportAnomalyItem(
                        day = reportDay(point.date),
                        value = point.revenue,
                        z = z,
                        kind = if (z > 0) ReportAnomalyKind.High else ReportAnomalyKind.Low
                    )
                )
            }
        }
        val m = ReportStats.mean(series) ?: 0.0
        anomalies = ReportAnalysis.Ready(
            ReportAnomalies(
                low = max(0.0, m - sd),
                high = m + sd,
                items = items.take(10),
                cv = ReportStats.coefVariation(series),
                confidence = ReportStats.confidenceFromSample(historyDays, ReportStats.MIN.anomalyDays, 60)
            )
        )
    }

    // Saisonnalité hebdomadaire
    var seasonality: ReportAnalysis<ReportSeasonality> =
        unavailable(ReportStats.MIN.weekdayWeeks * 7, "analyser la saisonnalité (jours de la semaine)")
    if (historyDays >= ReportStats.MIN.weekdayWeeks * 7 && activeDays >= 10) {
        val overall = ReportStats.mean(series) ?: 0.0
        val index = REPORT_ANALYSIS_WEEKDAYS.mapIndexed { i, label ->
            val values = allDaily.filter { it.date.dayOfWeek.value % 7 == i }.map { it.revenue }
            val mean = ReportStats.mean(values)
            ReportWeekdayIndex(
                label,
                if (overall > 0 && mean != null) (mean - overall) / overall * 100 else 0.0
            )
        }
        seasonality = ReportAnalysis.Ready(
            ReportSeasonality(
                weekday = index,
                confidence = ReportStats.confidenceFromSample(historyDays, 28, 90),
                monthNote = if (historyDays >= ReportStats.MIN.seasonMonths * 30) {
                    "Historique suffisant pour une analyse mensuelle."
                } else {
                    "Saisonnalité mensuelle indisponible : ${ReportStats.MIN.seasonMonths} mois d'historique sont nécessaires."
                }
            )
        )
    }

    // Prévision simple (moyenne des 14 derniers jours), explicable
    var forecast: ReportAnalysis<ReportForecast> =
        unavailable(ReportStats.MIN.forecastDays, "estimer les ventes futures")
    if (historyDays >= ReportStats.MIN.forecastDays && activeDays >= 10 && sd != null) {
        val base = ReportStats.mean(series.takeLast(14)) ?: 0.0
        val days = 7
        val expected = max(0.0, base * days)
        val err = sd * sqrt(days.toDouble())
        forecast = ReportAnalysis.Ready(
            ReportForecast(
                nextDays = days,
                expectedRevenue = expected,
                low = max(0.0, expected - err),
                high = expected + err,
                confidence = ReportStats.confidenceFromSample(historyDays, ReportStats.MIN.forecastDays, 90)
            )
        )
    }

    return ReportAnalyses(trend, variability, anomalies, seasonality, forecast)
}
