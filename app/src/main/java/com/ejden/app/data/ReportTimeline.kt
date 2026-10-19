package com.ejden.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.max

private val REPORT_WEEKDAY_NAMES = listOf("Dimanche", "Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi")

/** Première vente non annulée connue (sans filtre) et nombre de jours d'historique. */
data class ReportHistory(val firstAt: Long?, val days: Int)

fun computeReportHistory(
    sales: List<Sale>,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): ReportHistory {
    val first = sales.filter { !it.isCancelled }
        .mapNotNull { parseDateMillis(it.createdAt) }
        .minOrNull() ?: return ReportHistory(null, 0)
    val days = max(1, ChronoUnit.DAYS.between(statDay(first, zone), today).toInt() + 1)
    return ReportHistory(first, days)
}

/** Comparaison : seulement si la période comparée a de la donnée ET existe dans l'historique. */
data class ReportComparison(
    val previous: ReportTotals?,
    val note: String?,
    val views: List<ReportSaleView>
)

fun computeReportComparison(
    sales: List<Sale>,
    filters: ReportFilters,
    compare: ReportRange?,
    categoryOf: Map<String, String>,
    firstAt: Long?,
    zone: ZoneId = ZoneId.systemDefault()
): ReportComparison {
    if (compare == null) return ReportComparison(null, null, emptyList())
    val views = buildSaleViews(sales, filters, compare, categoryOf, zone)
    return when {
        firstAt == null || compare.endMillis(zone) < firstAt ->
            ReportComparison(null, "Comparaison indisponible : aucune donnée n'existe avant cette période.", views)
        views.isEmpty() ->
            ReportComparison(null, "Comparaison indisponible : aucune vente sur la période comparée.", views)
        else -> ReportComparison(computeTotals(views), null, views)
    }
}

data class ReportDayPoint(
    val day: String,
    val date: LocalDate,
    val label: String,
    val revenue: Double,
    val count: Int
)

data class ReportPeriodPoint(val label: String, val revenue: Double, val count: Int)
data class ReportWeekdayPoint(val label: String, val revenue: Double, val count: Int, val avgPerDay: Double)
data class ReportHourPoint(val hour: Int, val count: Int, val revenue: Double)

data class ReportTimeline(
    val daily: List<ReportDayPoint>,
    val weekly: List<ReportPeriodPoint>,
    val monthly: List<ReportPeriodPoint>,
    val weekdays: List<ReportWeekdayPoint>,
    val hours: List<ReportHourPoint>
)

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

fun reportDailySeries(
    views: List<ReportSaleView>,
    range: ReportRange,
    zone: ZoneId = ZoneId.systemDefault()
): List<ReportDayPoint> {
    val revenue = HashMap<LocalDate, Double>()
    val count = HashMap<LocalDate, Int>()
    for (view in views) {
        if (view.cancelled) continue
        val day = statDay(view.at, zone)
        revenue[day] = (revenue[day] ?: 0.0) + view.amount
        count[day] = (count[day] ?: 0) + 1
    }
    val out = ArrayList<ReportDayPoint>()
    var d = range.start
    while (!d.isAfter(range.end)) {
        out.add(
            ReportDayPoint(
                day = d.toString(),
                date = d,
                label = "${twoDigits(d.dayOfMonth)}/${twoDigits(d.monthValue)}",
                revenue = revenue[d] ?: 0.0,
                count = count[d] ?: 0
            )
        )
        d = d.plusDays(1)
    }
    return out
}

private class GroupAcc(val label: String) {
    var revenue = 0.0
    var count = 0
}

private fun groupPoints(
    points: List<ReportDayPoint>,
    keyOf: (LocalDate) -> Pair<String, String>
): List<ReportPeriodPoint> {
    val map = LinkedHashMap<String, GroupAcc>()
    for (point in points) {
        val (key, label) = keyOf(point.date)
        val e = map.getOrPut(key) { GroupAcc(label) }
        e.revenue += point.revenue
        e.count += point.count
    }
    return map.entries.sortedBy { it.key }.map { ReportPeriodPoint(it.value.label, it.value.revenue, it.value.count) }
}

fun buildReportTimeline(
    views: List<ReportSaleView>,
    range: ReportRange,
    zone: ZoneId = ZoneId.systemDefault()
): ReportTimeline {
    val daily = reportDailySeries(views, range, zone)

    val weekly = groupPoints(daily) { d ->
        val w = statWeekStart(d)
        w.toString() to "Sem. ${twoDigits(w.dayOfMonth)}/${twoDigits(w.monthValue)}"
    }
    val monthly = groupPoints(daily) { d ->
        "${d.year}-${twoDigits(d.monthValue - 1)}" to "${STAT_MONTHS[d.monthValue - 1]} ${d.year}"
    }

    val weekdays = REPORT_WEEKDAY_NAMES.mapIndexed { index, label ->
        val days = daily.filter { it.date.dayOfWeek.value % 7 == index }
        val revenue = days.sumOf { it.revenue }
        ReportWeekdayPoint(
            label, revenue, days.sumOf { it.count },
            if (days.isNotEmpty()) revenue / days.size else 0.0
        )
    }

    val hourCount = IntArray(24)
    val hourRevenue = DoubleArray(24)
    for (view in views) {
        if (view.cancelled) continue
        val hour = Instant.ofEpochMilli(view.at).atZone(zone).hour
        hourCount[hour] += 1
        hourRevenue[hour] += view.amount
    }
    val hours = (0 until 24).map { ReportHourPoint(it, hourCount[it], hourRevenue[it]) }

    return ReportTimeline(daily, weekly, monthly, weekdays, hours)
}
