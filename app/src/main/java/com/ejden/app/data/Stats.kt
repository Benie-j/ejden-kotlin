package com.ejden.app.data

import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Une vente non annulée avec sa date en millisecondes (Row dans statistiques.ts). */
data class StatRow(val sale: Sale, val at: Long)

fun activeStatRows(sales: List<Sale>): List<StatRow> {
    val rows = ArrayList<StatRow>()
    for (sale in sales) {
        if (sale.isCancelled) continue
        val at = parseDateMillis(sale.createdAt) ?: continue
        rows.add(StatRow(sale, at))
    }
    return rows
}

fun statDay(millis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** Lundi = 0 … dimanche = 6. */
fun statWeekday(date: LocalDate): Int = date.dayOfWeek.value - 1

fun statWeekStart(date: LocalDate): LocalDate = date.minusDays(statWeekday(date).toLong())

/** Équivalent de toLocaleString("fr-FR", { maximumFractionDigits }). */
fun statNumber(value: Double, maxDecimals: Int = 0): String {
    val symbols = DecimalFormatSymbols(Locale.ROOT).apply {
        decimalSeparator = ','
        groupingSeparator = '\u202F'
        minusSign = '-'
    }
    val pattern = if (maxDecimals > 0) "#,##0." + "#".repeat(maxDecimals) else "#,##0"
    val format = DecimalFormat(pattern, symbols)
    format.roundingMode = RoundingMode.HALF_UP
    return format.format(value)
}

fun statCompact(value: Double): String {
    val v = abs(value)
    val s = if (value < 0) "\u2212" else ""
    return when {
        v >= 1e6 -> s + statNumber(v / 1e6, 1) + " M"
        v >= 1e3 -> s + Math.round(v / 1e3).toString() + " k"
        else -> s + Math.round(v).toString()
    }
}

fun statPct(value: Double, digits: Int = 0): String = statNumber(value, digits) + " %"

fun statSignedPct(value: Double): String = (if (value > 0) "+" else "") + statPct(value, 1)

fun statQty(value: Double): String = statNumber(value, 1)

data class LinearFit(val slope: Double, val intercept: Double, val sd: Double)

/** Régression linéaire simple : tendance + écart-type des résidus. */
fun linearFit(y: List<Double>): LinearFit {
    val n = y.size
    if (n == 0) return LinearFit(0.0, 0.0, 0.0)
    val xMean = (n - 1) / 2.0
    val yMean = y.sum() / n
    var num = 0.0
    var den = 0.0
    y.forEachIndexed { x, value ->
        num += (x - xMean) * (value - yMean)
        den += (x - xMean) * (x - xMean)
    }
    val slope = if (den == 0.0) 0.0 else num / den
    val intercept = yMean - slope * xMean
    var squares = 0.0
    y.forEachIndexed { x, value ->
        val r = value - (intercept + slope * x)
        squares += r * r
    }
    val sd = if (n > 2) sqrt(squares / (n - 2)) else 0.0
    return LinearFit(slope, intercept, sd)
}

data class Projection(val values: List<Double>, val sd: Double, val slope: Double, val mean: Double)

fun project(y: List<Double>, count: Int): Projection {
    if (y.isEmpty()) return Projection(List(count) { 0.0 }, 0.0, 0.0, 0.0)
    val fit = linearFit(y)
    val cap = (y.maxOrNull() ?: 0.0) * 2
    val values = ArrayList<Double>()
    for (k in 1..count) {
        values.add(min(cap, max(0.0, fit.intercept + fit.slope * (y.size - 1 + k))))
    }
    return Projection(values, fit.sd, fit.slope, y.sum() / y.size)
}
