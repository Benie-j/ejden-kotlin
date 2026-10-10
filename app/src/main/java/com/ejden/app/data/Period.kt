package com.ejden.app.data

import org.json.JSONObject
import org.json.JSONTokener
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

enum class PeriodPreset(val id: String) {
    Today("today"), Days7("7d"), Month1("1m"), Months3("3m"), Months6("6m"), Custom("custom");

    companion object {
        fun fromId(value: Any?): PeriodPreset? = entries.firstOrNull { it.id == value }
    }
}

data class PeriodChoice(
    val preset: PeriodPreset,
    /** Pour « Custom » seulement : jours inclus, au format AAAA-MM-JJ. */
    val from: String? = null,
    val to: String? = null
)

private val DAY_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")

fun isValidDay(value: Any?): Boolean {
    if (value !is String || !DAY_PATTERN.matches(value)) return false
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    if (year < 2000 || year > 2100) return false
    return try {
        LocalDate.of(year, month, day)
        true
    } catch (e: DateTimeException) {
        false
    }
}

class PeriodRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_dashboard_period"
    }

    fun get(): PeriodChoice {
        val fallback = PeriodChoice(PeriodPreset.Today)
        val stored = store.get(KEY)
        if (stored == null || stored.isEmpty()) return fallback

        return try {
            val parsed = JSONTokener(stored).nextValue() as? JSONObject ?: return fallback
            val preset = PeriodPreset.fromId(parsed.opt("preset")) ?: return fallback

            if (preset == PeriodPreset.Custom) {
                val from = parsed.opt("from")
                val to = parsed.opt("to")
                if (isValidDay(from) && isValidDay(to) && (from as String) <= (to as String)) {
                    PeriodChoice(PeriodPreset.Custom, from, to)
                } else {
                    fallback
                }
            } else {
                PeriodChoice(preset)
            }
        } catch (e: Exception) {
            fallback
        }
    }

    fun save(choice: PeriodChoice): Boolean {
        val json = JSONObject()
            .put("preset", choice.preset.id)
            .put("from", choice.from ?: JSONObject.NULL)
            .put("to", choice.to ?: JSONObject.NULL)
        return store.set(KEY, json.toString())
    }
}

data class ResolvedPeriod(
    val startDay: LocalDate,
    val endDay: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    /** Période précédente de même durée (pour comparer). */
    val previousStartMillis: Long,
    val previousEndMillis: Long,
    /** Texte court des cartes : « Aujourd'hui », « 7 jours »... */
    val label: String,
    /** Texte du bouton : « Aujourd'hui », « 12 sept – 3 oct »... */
    val buttonLabel: String,
    /** Fin de la phrase de comparaison : « hier » ou « période précédente ». */
    val comparedTo: String
)

private val SHORT_LABELS = mapOf(
    PeriodPreset.Today to "Aujourd'hui",
    PeriodPreset.Days7 to "7 jours",
    PeriodPreset.Month1 to "1 mois",
    PeriodPreset.Months3 to "3 mois",
    PeriodPreset.Months6 to "6 mois",
    PeriodPreset.Custom to "Personnalisée"
)

private fun shortDate(date: LocalDate, withYear: Boolean): String {
    val pattern = if (withYear) "d MMM yyyy" else "d MMM"
    return DateTimeFormatter.ofPattern(pattern, Locale.FRANCE).format(date).replaceFirst(".", "")
}

/** Les jours sont comptés en heure locale, bornes incluses. */
fun resolvePeriod(
    choice: PeriodChoice,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): ResolvedPeriod {
    var start = today
    var end = today

    when (choice.preset) {
        PeriodPreset.Today -> Unit
        PeriodPreset.Days7 -> start = today.minusDays(6)
        PeriodPreset.Month1 -> start = today.minusMonths(1).plusDays(1)
        PeriodPreset.Months3 -> start = today.minusMonths(3).plusDays(1)
        PeriodPreset.Months6 -> start = today.minusMonths(6).plusDays(1)
        PeriodPreset.Custom -> {
            if (choice.from != null && choice.to != null) {
                start = LocalDate.parse(choice.from)
                end = LocalDate.parse(choice.to)
            }
        }
    }

    fun dayStart(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()
    fun dayEnd(day: LocalDate): Long = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

    // Durée en jours (bornes incluses) ; la période précédente la précède directement.
    val days = ChronoUnit.DAYS.between(start, end) + 1
    val previousEnd = start.minusDays(1)
    val previousStart = start.minusDays(days)

    val isCustom = choice.preset == PeriodPreset.Custom
    val sameYear = start.year == end.year && end.year == today.year

    val customLabel = if (start == end) {
        shortDate(start, !sameYear)
    } else {
        shortDate(start, !sameYear) + " \u2013 " + shortDate(end, !sameYear)
    }
    val label = if (isCustom) customLabel else (SHORT_LABELS[choice.preset] ?: "")

    return ResolvedPeriod(
        startDay = start,
        endDay = end,
        startMillis = dayStart(start),
        endMillis = dayEnd(end),
        previousStartMillis = dayStart(previousStart),
        previousEndMillis = dayEnd(previousEnd),
        label = label,
        buttonLabel = label,
        comparedTo = if (choice.preset == PeriodPreset.Today) "hier" else "période précédente"
    )
}

/** Variation en % entre deux totaux (null si la base est nulle). */
fun percentChange(current: Double, previous: Double): Int? {
    if (previous <= 0.0) return null
    return Math.round((current - previous) / previous * 100).toInt()
}

enum class ChangeTone { Up, Down, Neutral }

data class Change(val text: String, val tone: ChangeTone)

/** Texte et style d'une variation : ▲ +12 %, ▼ −8 %, « Nouveau »... */
fun describeChange(current: Double, previous: Double): Change {
    val change = percentChange(current, previous)

    if (change == null) {
        return if (current > 0.0) Change("Nouveau", ChangeTone.Up) else Change("\u2014", ChangeTone.Neutral)
    }
    if (change > 0) return Change("\u25B2 +$change %", ChangeTone.Up)
    if (change < 0) return Change("\u25BC \u2212${abs(change)} %", ChangeTone.Down)
    return Change("= stable", ChangeTone.Neutral)
}

data class CashFlow(val cashIn: Double, val cashOut: Double)

/** Argent entré et sorti de la caisse sur une période. */
fun CashSummary.flowBetween(fromMillis: Long, toMillis: Long): CashFlow {
    var cashIn = 0.0
    var cashOut = 0.0
    for (movement in movements) {
        val at = parseDateMillis(movement.createdAt) ?: continue
        if (at < fromMillis || at > toMillis) continue
        if (movement.amount > 0.0) cashIn += movement.amount else cashOut += -movement.amount
    }
    return CashFlow(cashIn, cashOut)
}

data class DashboardData(
    val summary: SalesSummary,
    val previous: SalesSummary,
    val cashBalance: Double,
    val cashFlow: CashFlow,
    val clientsCount: Int,
    val receivables: ReceivablesSummary,
    val stock: StockSummary
) {
    companion object {
        fun empty() = DashboardData(
            SalesSummary(0, 0.0, null),
            SalesSummary(0, 0.0, null),
            0.0,
            CashFlow(0.0, 0.0),
            0,
            ReceivablesSummary(0.0, 0),
            StockSummary(0, 0, 0, emptyList())
        )
    }
}

class DashboardRepository(
    private val sales: SaleRepository,
    private val cash: CashRepository,
    private val clients: ClientRepository,
    private val credits: CreditPaymentRepository,
    private val products: ProductRepository
) {
    fun load(period: ResolvedPeriod): DashboardData {
        val cashSummary = cash.getCashSummary()
        return DashboardData(
            summary = sales.getSalesSummaryBetween(period.startMillis, period.endMillis),
            previous = sales.getSalesSummaryBetween(period.previousStartMillis, period.previousEndMillis),
            cashBalance = cashSummary.balance,
            cashFlow = cashSummary.flowBetween(period.startMillis, period.endMillis),
            clientsCount = clients.getClients().size,
            receivables = getReceivables(sales, credits),
            stock = getStockSummary(products, 4)
        )
    }
}
