package com.ejden.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

val STAT_MONTHS = listOf(
    "janv.", "févr.", "mars", "avr.", "mai", "juin",
    "juil.", "août", "sept.", "oct.", "nov.", "déc."
)
val STAT_WEEKDAYS = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

private const val DAY_MS = 86_400_000L

private fun LocalDate.startMillis(zone: ZoneId): Long =
    atStartOfDay(zone).toInstant().toEpochMilli()

enum class Trend { Up, Down, Flat }

fun trendOf(slope: Double, mean: Double): Trend {
    val rel = if (mean > 0) slope / mean * 100 else 0.0
    return if (rel > 3) Trend.Up else if (rel < -3) Trend.Down else Trend.Flat
}

data class ShortTermForecast(
    val daysCovered: Int,
    val next7: Double,
    val next30: Double,
    val realized: Double,
    val remaining: Double,
    val monthIndex: Int
) {
    val projectedMonth: Double get() = realized + remaining
}

data class ChartSeries(
    val labels: List<String>,
    val values: List<Double>,
    val forecast: List<Double>,
    val forecastLabels: List<String>,
    val sd: Double,
    val slope: Double,
    val mean: Double
)

data class WeeklyForecast(val chart: ChartSeries, val trend: Trend, val forecastTotal: Double)

data class MonthlyForecast(
    val chart: ChartSeries,
    val lastVsPrevPct: Double?,
    val recent3VsBefore3Pct: Double?,
    val yearOnYearPct: Double?,
    val bestLabel: String,
    val best: Double,
    val worstLabel: String,
    val worst: Double,
    val forecastTotal: Double
)

sealed interface BreakEven {
    data object NoData : BreakEven
    data object NoCharges : BreakEven
    data class Ready(
        val monthlyCharges: Double,
        val marginRate: Double,
        val threshold: Double,
        val projectedMonth: Double?
    ) : BreakEven {
        val ratio: Double? get() = projectedMonth?.let { it / threshold }
    }
}

data class Rhythm(
    val grid: List<List<Int>>,
    val maxCount: Int,
    val bestDay: Int,
    val bestDayAverage: Double,
    val worstDay: Int,
    val worstDayAverage: Double,
    val bestHour: Int
)

data class ForecastData(
    val shortTerm: ShortTermForecast,
    val weekly: WeeklyForecast?,
    val monthly: MonthlyForecast?,
    val breakEven: BreakEven,
    val rhythm: Rhythm?
)

private fun changePct(now: Double, before: Double): Double = (now - before) / before * 100

/** Retourne null s'il n'y a aucune vente (le HTML affiche alors un message vide). */
fun computeForecast(
    rows: List<StatRow>,
    expenses: List<Expense>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault()
): ForecastData? {
    if (rows.isEmpty()) return null

    val today = statDay(nowMillis, zone)
    val todayMs = today.startMillis(zone)
    val first = rows.minOf { it.at }

    /* Court terme : moyenne par jour de la semaine sur 8 semaines */
    val from = maxOf(statDay(first, zone), today.minusDays(56))
    val fromMs = from.startMillis(zone)
    val daysCovered = ChronoUnit.DAYS.between(from, today).toInt()
    val perDay = DoubleArray(7)
    val dayCount = IntArray(7)

    var d = from
    while (d.isBefore(today)) {
        dayCount[statWeekday(d)] += 1
        d = d.plusDays(1)
    }
    for (r in rows) {
        if (r.at >= fromMs && r.at < todayMs) perDay[statWeekday(statDay(r.at, zone))] += r.sale.total
    }
    val avg = List(7) { i -> if (dayCount[i] > 0) perDay[i] / dayCount[i] else 0.0 }

    var next7 = 0.0
    var next30 = 0.0
    for (i in 1..30) {
        val v = avg[statWeekday(today.plusDays(i.toLong()))]
        next30 += v
        if (i <= 7) next7 += v
    }

    val monthFirst = today.withDayOfMonth(1)
    val monthEnd = YearMonth.from(today).atEndOfMonth()
    val monthStartMs = monthFirst.startMillis(zone)
    val realized = rows.filter { it.at >= monthStartMs }.sumOf { it.sale.total }
    var remaining = 0.0
    var day = today.plusDays(1)
    while (!day.isAfter(monthEnd)) {
        remaining += avg[statWeekday(day)]
        day = day.plusDays(1)
    }
    val shortTerm = ShortTermForecast(
        daysCovered, next7, next30, realized, remaining, today.monthValue - 1
    )

    /* Semaines */
    val w0 = statWeekStart(today)
    val weekly = ArrayList<Double>()
    val weekLabels = ArrayList<String>()
    for (i in 12 downTo 1) {
        val start = w0.minusDays(7L * i)
        val sMs = start.startMillis(zone)
        val eMs = start.plusDays(7).startMillis(zone)
        weekly.add(rows.filter { it.at >= sMs && it.at < eMs }.sumOf { it.sale.total })
        weekLabels.add("${start.dayOfMonth}/${start.monthValue}")
    }
    val firstWeek = weekly.indexOfFirst { it > 0 }
    val wSeries = if (firstWeek == -1) emptyList() else weekly.subList(firstWeek, weekly.size).toList()
    var weeklyResult: WeeklyForecast? = null
    if (wSeries.size >= 4) {
        val p = project(wSeries, 4)
        val future = (1..4).map { k ->
            val dd = w0.plusDays(7L * (k - 1))
            "${dd.dayOfMonth}/${dd.monthValue}"
        }
        weeklyResult = WeeklyForecast(
            ChartSeries(
                weekLabels.subList(firstWeek, weekLabels.size).toList(),
                wSeries, p.values, future, p.sd, p.slope, p.mean
            ),
            trendOf(p.slope, p.mean),
            p.values.sum()
        )
    }

    /* Mois et croissance */
    val monthly = ArrayList<Double>()
    val monthLabels = ArrayList<String>()
    for (i in 12 downTo 1) {
        val start = monthFirst.minusMonths(i.toLong())
        val end = monthFirst.minusMonths((i - 1).toLong())
        val sMs = start.startMillis(zone)
        val eMs = end.startMillis(zone)
        monthly.add(rows.filter { it.at >= sMs && it.at < eMs }.sumOf { it.sale.total })
        monthLabels.add(STAT_MONTHS[start.monthValue - 1])
    }
    val firstMonth = monthly.indexOfFirst { it > 0 }
    val mSeries = if (firstMonth == -1) emptyList() else monthly.subList(firstMonth, monthly.size).toList()
    var monthlyResult: MonthlyForecast? = null
    if (mSeries.size >= 3) {
        val p = project(mSeries, 3)
        val future = (0..2).map { k -> STAT_MONTHS[(today.monthValue - 1 + k) % 12] }
        val last = mSeries.last()
        val prev = mSeries[mSeries.size - 2]
        val best = mSeries.max()
        val worst = mSeries.min()
        val offset = monthly.size - mSeries.size
        val recent3 = mSeries.takeLast(3).sum()
        val before3 = if (mSeries.size >= 6) mSeries.subList(mSeries.size - 6, mSeries.size - 3).sum() else 0.0
        val yoyBase = if (monthly.size >= 12) monthly[0] else 0.0
        monthlyResult = MonthlyForecast(
            chart = ChartSeries(
                monthLabels.subList(offset, monthLabels.size).toList(),
                mSeries, p.values, future, p.sd, p.slope, p.mean
            ),
            lastVsPrevPct = if (prev > 0) changePct(last, prev) else null,
            recent3VsBefore3Pct = if (before3 > 0) changePct(recent3, before3) else null,
            yearOnYearPct = if (yoyBase > 0) changePct(last, yoyBase) else null,
            bestLabel = monthLabels[offset + mSeries.indexOf(best)],
            best = best,
            worstLabel = monthLabels[offset + mSeries.indexOf(worst)],
            worst = worst,
            forecastTotal = p.values.sum()
        )
    }

    /* Seuil de rentabilité */
    val from3Ms = monthFirst.minusMonths(3).startMillis(zone)
    val charges = expenses.filter { e ->
        val at = parseDateMillis(e.createdAt)
        e.category != ExpenseCategory.Merchandise && at != null && at >= from3Ms && at < monthStartMs
    }.sumOf { it.amount }
    val cutoff = nowMillis - 90 * DAY_MS
    val costed = rows.filter { it.at >= cutoff && it.sale.items.all { i -> i.unitCost != null } }
    val revenue = costed.sumOf { it.sale.total }
    val margin = costed.sumOf { r ->
        r.sale.items.sumOf { i -> (i.unitPrice - (i.unitCost ?: 0.0)) * i.quantity } - r.sale.discount
    }
    val breakEven: BreakEven = when {
        revenue <= 0 || margin <= 0 -> BreakEven.NoData
        charges <= 0 -> BreakEven.NoCharges
        else -> {
            val monthlyCharges = charges / 3
            val rate = margin / revenue
            BreakEven.Ready(
                monthlyCharges, rate, monthlyCharges / rate,
                if (daysCovered >= 14) shortTerm.projectedMonth else null
            )
        }
    }

    /* Jours et heures de pointe */
    val grid = Array(7) { IntArray(12) }
    val hourly = DoubleArray(24)
    var total = 0
    for (r in rows) {
        if (r.at < cutoff) continue
        val z = Instant.ofEpochMilli(r.at).atZone(zone)
        grid[statWeekday(z.toLocalDate())][z.hour / 2] += 1
        hourly[z.hour] += r.sale.total
        total += 1
    }
    var rhythm: Rhythm? = null
    if (total >= 10) {
        val hourList = hourly.toList()
        val bestDay = avg.indexOf(avg.max())
        val worstDay = avg.indexOf(avg.min())
        rhythm = Rhythm(
            grid = grid.map { it.toList() },
            maxCount = grid.maxOf { it.max() },
            bestDay = bestDay,
            bestDayAverage = avg[bestDay],
            worstDay = worstDay,
            worstDayAverage = avg[worstDay],
            bestHour = hourList.indexOf(hourList.max())
        )
    }

    return ForecastData(shortTerm, weeklyResult, monthlyResult, breakEven, rhythm)
}
