package com.ejden.app.data

import kotlin.math.abs
import kotlin.math.sqrt

enum class ReportConfidence(val label: String) {
    Low("faible"),
    Medium("moyenne"),
    High("élevée")
}

data class ReportTrend(val slope: Double, val intercept: Double, val r2: Double)

/**
 * Outils statistiques purs. Chaque fonction qui exige un minimum de données renvoie
 * null si l'échantillon est insuffisant : l'écran affiche alors « analyse indisponible ».
 */
object ReportStats {
    object MIN {
        const val trendDays = 14
        const val anomalyDays = 14
        const val weekdayWeeks = 4
        const val forecastDays = 21
        const val seasonMonths = 12
        const val clientRecurring = 2
    }

    fun sum(values: List<Double>): Double = values.sum()

    fun mean(values: List<Double>): Double? = if (values.isEmpty()) null else values.sum() / values.size

    fun median(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun variance(values: List<Double>): Double? {
        val m = mean(values)
        if (m == null || values.size < 2) return null
        return values.sumOf { (it - m) * (it - m) } / (values.size - 1)
    }

    fun stdDev(values: List<Double>): Double? = variance(values)?.let { sqrt(it) }

    fun coefVariation(values: List<Double>): Double? {
        val m = mean(values)
        val s = stdDev(values)
        return if (m == null || s == null || m == 0.0) null else s / abs(m)
    }

    fun zScore(value: Double, sample: List<Double>): Double? {
        val m = mean(sample)
        val s = stdDev(sample)
        return if (m == null || s == null || s == 0.0) null else (value - m) / s
    }

    fun movingAverage(values: List<Double>, window: Int): List<Double?> =
        values.indices.map { i ->
            if (i + 1 < window) null else values.subList(i + 1 - window, i + 1).sum() / window
        }

    /** Régression linéaire simple : pente par pas de temps et R². */
    fun linearTrend(values: List<Double>): ReportTrend? {
        val n = values.size
        if (n < 3) return null
        val mx = (n - 1) / 2.0
        val my = values.sum() / n
        var sxy = 0.0
        var sxx = 0.0
        var syy = 0.0
        values.forEachIndexed { x, y ->
            sxy += (x - mx) * (y - my)
            sxx += (x - mx) * (x - mx)
            syy += (y - my) * (y - my)
        }
        if (sxx == 0.0) return null
        val slope = sxy / sxx
        return ReportTrend(slope, my - slope * mx, if (syy == 0.0) 0.0 else (sxy * sxy) / (sxx * syy))
    }

    fun percentChange(current: Double, previous: Double): Double? =
        if (previous == 0.0) null else (current - previous) / abs(previous) * 100

    fun confidenceFromSample(n: Int, low: Int, high: Int): ReportConfidence =
        if (n >= high) ReportConfidence.High else if (n >= low) ReportConfidence.Medium else ReportConfidence.Low

    fun formatPercent(value: Double, signed: Boolean = true): String {
        val text = statNumber(abs(value), 1)
        val sign = if (!signed) "" else if (value > 0) "+" else if (value < 0) "\u2212" else ""
        return "$sign$text %"
    }
}
