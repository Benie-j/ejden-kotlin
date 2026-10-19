package com.ejden.app.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class ReportPeriod(val id: String, val label: String) {
    Today("today", "Aujourd'hui"),
    Yesterday("yesterday", "Hier"),
    Week("week", "Cette semaine"),
    LastWeek("last_week", "Semaine précédente"),
    Month("month", "Ce mois"),
    LastMonth("last_month", "Mois précédent"),
    Year("year", "Cette année"),
    LastYear("last_year", "Année précédente"),
    Days7("7d", "7 derniers jours"),
    Days30("30d", "30 derniers jours"),
    Days90("90d", "90 derniers jours"),
    Custom("custom", "Période personnalisée");

    companion object {
        fun fromId(value: Any?): ReportPeriod? = entries.firstOrNull { it.id == value }
    }
}

enum class ReportCompare(val id: String, val label: String) {
    None("none", "Aucune comparaison"),
    Previous("previous", "Période précédente"),
    Duration("duration", "Même durée précédente"),
    LastYear("last_year", "Même période, année précédente"),
    Custom("custom", "Période personnalisée");

    companion object {
        fun fromId(value: Any?): ReportCompare? = entries.firstOrNull { it.id == value }
    }
}

/** Période en jours entiers, bornes incluses (équivalent de Range dans report-engine.ts). */
data class ReportRange(val start: LocalDate, val end: LocalDate) {
    val dayCount: Int get() = max(1, ChronoUnit.DAYS.between(start, end).toInt() + 1)

    fun startMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        start.atStartOfDay(zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
        end.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    fun contains(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
        millis >= startMillis(zone) && millis <= endMillis(zone)
}

/** Reproduit new Date(année, mois0, jour) de JavaScript, y compris les débordements. */
private fun jsDate(year: Int, month0: Int, day: Int): LocalDate =
    LocalDate.of(year, 1, 1).plusMonths(month0.toLong()).plusDays((day - 1).toLong())

fun resolveReportPeriod(
    key: ReportPeriod,
    custom: ReportRange? = null,
    today: LocalDate = LocalDate.now()
): ReportRange {
    val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val y = today.year
    val m = today.monthValue - 1

    return when (key) {
        ReportPeriod.Today -> ReportRange(today, today)
        ReportPeriod.Yesterday -> today.minusDays(1).let { ReportRange(it, it) }
        ReportPeriod.Week -> ReportRange(monday, today)
        ReportPeriod.LastWeek -> ReportRange(monday.minusDays(7), monday.minusDays(1))
        ReportPeriod.Month -> ReportRange(jsDate(y, m, 1), today)
        ReportPeriod.LastMonth -> ReportRange(jsDate(y, m - 1, 1), jsDate(y, m, 0))
        ReportPeriod.Year -> ReportRange(jsDate(y, 0, 1), today)
        ReportPeriod.LastYear -> ReportRange(jsDate(y - 1, 0, 1), jsDate(y - 1, 11, 31))
        ReportPeriod.Days7 -> ReportRange(today.minusDays(6), today)
        ReportPeriod.Days30 -> ReportRange(today.minusDays(29), today)
        ReportPeriod.Days90 -> ReportRange(today.minusDays(89), today)
        ReportPeriod.Custom -> {
            val c = custom ?: ReportRange(today, today)
            if (!c.start.isAfter(c.end)) ReportRange(c.start, c.end) else ReportRange(c.end, c.start)
        }
    }
}

fun resolveReportComparison(
    key: ReportCompare,
    period: ReportPeriod,
    current: ReportRange,
    custom: ReportRange? = null
): ReportRange? {
    when (key) {
        ReportCompare.None -> return null
        ReportCompare.Custom -> return custom?.let { ReportRange(it.start, it.end) }
        ReportCompare.LastYear -> return ReportRange(
            jsDate(current.start.year - 1, current.start.monthValue - 1, current.start.dayOfMonth),
            jsDate(current.end.year - 1, current.end.monthValue - 1, current.end.dayOfMonth)
        )
        else -> Unit
    }

    val n = current.dayCount

    // « Période précédente » respecte le calendrier pour mois / année.
    if (key == ReportCompare.Previous) {
        if (period == ReportPeriod.Month || period == ReportPeriod.LastMonth) {
            val s = jsDate(current.start.year, current.start.monthValue - 2, 1)
            val lastDay = s.lengthOfMonth()
            val e = jsDate(s.year, s.monthValue - 1, min(current.end.dayOfMonth, lastDay))
            val end = if (period == ReportPeriod.LastMonth) s.withDayOfMonth(lastDay) else e
            return ReportRange(s, end)
        }
        if (period == ReportPeriod.Year || period == ReportPeriod.LastYear) {
            val s = jsDate(current.start.year - 1, 0, 1)
            return ReportRange(s, jsDate(s.year, current.end.monthValue - 1, current.end.dayOfMonth))
        }
    }

    return ReportRange(current.start.minusDays(n.toLong()), current.start.minusDays(1))
}

private val REPORT_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)

fun reportDay(date: LocalDate): String = date.format(REPORT_DAY_FORMAT)

fun reportRangeLabel(range: ReportRange): String =
    if (range.dayCount == 1) reportDay(range.start)
    else "${reportDay(range.start)} → ${reportDay(range.end)}"
