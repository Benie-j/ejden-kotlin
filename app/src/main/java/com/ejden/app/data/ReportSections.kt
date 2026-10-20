package com.ejden.app.data

import kotlin.math.abs

enum class ReportTone { Up, Down }

data class ReportKpi(
    val id: String,
    val label: String,
    val value: String,
    val delta: String? = null,
    val tone: ReportTone? = null
)

/** Cellules : String ou nombre (Long pour les entiers, Double sinon), comme les nombres du HTML. */
data class ReportTable(
    val id: String,
    val title: String,
    val headers: List<String>,
    val rows: List<List<Any>>,
    val note: String? = null
)

data class ReportChart(val title: String, val labels: List<String>, val values: List<Double>)

data class ReportSection(
    val id: ReportSectionId,
    val label: String,
    val kpis: List<ReportKpi>,
    val tables: List<ReportTable>,
    val chart: ReportChart? = null,
    val message: String? = null
)

internal fun sectionInt(value: Int): String = statNumber(value.toDouble())

internal fun sectionFcfa(value: Double): String = statNumber(Math.round(value).toDouble()) + " FCFA"

internal fun sectionCell(value: Double): Any =
    if (value == Math.floor(value) && abs(value) < 1e15) value.toLong() else value

internal fun sectionCell(value: Int): Any = value.toLong()

internal fun sectionKpiWithDelta(
    id: String,
    label: String,
    value: String,
    current: Double,
    previous: Double?
): ReportKpi {
    if (previous == null) return ReportKpi(id, label, value)
    val change = ReportStats.percentChange(current, previous) ?: return ReportKpi(id, label, value)
    val arrow = if (change > 0) "↑" else if (change < 0) "↓" else "="
    return ReportKpi(
        id, label, value,
        "$arrow ${ReportStats.formatPercent(change)}",
        if (change > 0) ReportTone.Up else if (change < 0) ReportTone.Down else null
    )
}

fun buildSalesSection(r: ReportData, permissions: ReportPermissions): ReportSection {
    val t = r.totals
    val prev = r.previous
    val tl = r.timeline

    val kpis = ArrayList<ReportKpi>()
    kpis.add(sectionKpiWithDelta("revenue", "Chiffre d'affaires", sectionFcfa(t.revenue), t.revenue, prev?.revenue))
    kpis.add(sectionKpiWithDelta("count", "Transactions", sectionInt(t.count), t.count.toDouble(), prev?.count?.toDouble()))
    kpis.add(sectionKpiWithDelta("units", "Produits vendus", statNumber(t.units, 1), t.units, prev?.units))
    kpis.add(sectionKpiWithDelta("average", "Panier moyen", sectionFcfa(t.average), t.average, prev?.average))
    kpis.add(ReportKpi("cancelled", "Ventes annulées", "${t.cancelledCount} (${sectionFcfa(t.cancelledAmount)})"))
    if (permissions.viewFinancialReports) {
        kpis.add(ReportKpi("margin", "Marge brute", t.margin?.let { sectionFcfa(it) } ?: "—"))
    }

    val weekly = tl.daily.size > 62
    val chart = if (weekly) {
        ReportChart("Chiffre d'affaires par semaine", tl.weekly.map { it.label }, tl.weekly.map { it.revenue })
    } else {
        ReportChart("Chiffre d'affaires par jour", tl.daily.map { it.label }, tl.daily.map { it.revenue })
    }

    val tables = listOf(
        ReportTable(
            "by-day", "Ventes par jour", listOf("Jour", "Transactions", "CA (FCFA)"),
            tl.daily.filter { it.count > 0 }
                .map { listOf<Any>(reportDay(it.date), sectionCell(it.count), Math.round(it.revenue)) }
        ),
        ReportTable(
            "by-week", "Ventes par semaine", listOf("Semaine", "Transactions", "CA (FCFA)"),
            tl.weekly.filter { it.count > 0 }
                .map { listOf<Any>(it.label, sectionCell(it.count), Math.round(it.revenue)) }
        ),
        ReportTable(
            "by-month", "Ventes par mois", listOf("Mois", "Transactions", "CA (FCFA)"),
            tl.monthly.filter { it.count > 0 }
                .map { listOf<Any>(it.label, sectionCell(it.count), Math.round(it.revenue)) }
        ),
        ReportTable(
            "by-weekday", "Activité par jour de la semaine",
            listOf("Jour", "Transactions", "CA (FCFA)", "CA moyen/jour"),
            tl.weekdays.filter { it.count > 0 }.map {
                listOf<Any>(it.label, sectionCell(it.count), Math.round(it.revenue), Math.round(it.avgPerDay))
            }
        ),
        ReportTable(
            "by-hour", "Activité par heure", listOf("Heure", "Transactions", "CA (FCFA)"),
            tl.hours.filter { it.count > 0 }.map {
                listOf<Any>("${it.hour.toString().padStart(2, '0')}h", sectionCell(it.count), Math.round(it.revenue))
            }
        )
    )

    return ReportSection(ReportSectionId.Sales, "Ventes", kpis, tables, chart)
}

fun buildProductsSection(r: ReportData): ReportSection {
    val rows = r.products.rows
    val byQty = rows.sortedByDescending { it.qty }
    val rising = rows
        .filter {
            val before = it.prevQty
            before != null && before > 0 && it.qty > before
        }
        .sortedByDescending { it.qty / it.prevQty!! }
    val falling = rows
        .filter {
            val before = it.prevQty
            before != null && before > 0 && it.qty < before
        }
        .sortedBy { it.qty / it.prevQty!! }

    fun productRow(x: ReportProductRow): List<Any> = listOf(
        x.name,
        x.category,
        sectionCell(x.qty),
        Math.round(x.amount),
        sectionCell(Math.round(x.share * 10) / 10.0),
        sectionCell(Math.round(x.frequency * 1000) / 10.0)
    )

    val headers = listOf("Produit", "Catégorie", "Qté vendue", "CA (FCFA)", "Part (%)", "Fréquence (% jours)")
    val hasAbc = rows.any { it.abc != null }

    val tables = ArrayList<ReportTable>()
    tables.add(ReportTable("top", "Produits les plus vendus", headers, rows.map { productRow(it) }))
    tables.add(ReportTable("bottom", "Produits les moins vendus", headers, byQty.reversed().map { productRow(it) }))
    if (r.previous != null) {
        val compareHeaders = listOf("Produit", "Qté", "Qté période comparée")
        tables.add(
            ReportTable(
                "rising", "Produits en progression", compareHeaders,
                rising.map { listOf<Any>(it.name, sectionCell(it.qty), sectionCell(it.prevQty ?: 0.0)) }
            )
        )
        tables.add(
            ReportTable(
                "falling", "Produits en baisse", compareHeaders,
                falling.map { listOf<Any>(it.name, sectionCell(it.qty), sectionCell(it.prevQty ?: 0.0)) }
            )
        )
    }
    tables.add(
        ReportTable(
            "nosale", "Produits sans vente", listOf("Produit", "Stock actuel"),
            r.products.noSale.map { listOf<Any>(it.name, sectionCell(it.stock)) }
        )
    )
    if (hasAbc) {
        tables.add(
            ReportTable(
                "abc", "Classification ABC (80 / 15 / 5 % du CA)", listOf("Produit", "Classe", "Part (%)"),
                rows.map { listOf<Any>(it.name, it.abc ?: "", sectionCell(Math.round(it.share * 10) / 10.0)) }
            )
        )
    }

    return ReportSection(
        id = ReportSectionId.Products,
        label = "Produits",
        kpis = listOf(
            ReportKpi("p-sold", "Produits vendus (références)", sectionInt(rows.size)),
            ReportKpi("p-none", "Produits sans vente", sectionInt(r.products.noSale.size))
        ),
        tables = tables,
        message = if (hasAbc) null else "Classification ABC indisponible : au moins 10 produits vendus sont nécessaires."
    )
}

fun buildStockSection(r: ReportData): ReportSection {
    val st = r.stock

    val kpis = listOf(
        ReportKpi("s-value", "Valeur du stock (prix d'achat)", sectionFcfa(st.value)),
        ReportKpi("s-in", "Entrées (période)", statNumber(st.entries.toDouble(), 1)),
        ReportKpi("s-out", "Sorties (période)", statNumber(st.exits.toDouble(), 1)),
        ReportKpi("s-rupt", "Ruptures", st.rows.count { it.status == ReportStockStatus.Out }.toString()),
        ReportKpi("s-low", "Proches de la rupture", st.rows.count { it.status == ReportStockStatus.Low }.toString()),
        ReportKpi(
            "s-rot", "Rotation moyenne",
            st.rotation?.let { "${statNumber(it, 2)}×" } ?: "—"
        )
    )

    val reliability = if (r.historyDays >= 30) "Moyenne" else "Faible (historique limité)"

    val tables = listOf(
        ReportTable(
            "levels", "Stock actuel",
            listOf("Produit", "Stock", "Seuil", "Valeur (FCFA)", "Vendu (période)", "Statut"),
            st.rows.map {
                listOf<Any>(
                    it.name,
                    sectionCell(it.stock),
                    sectionCell(it.threshold),
                    Math.round(it.value),
                    sectionCell(it.sold),
                    when (it.status) {
                        ReportStockStatus.Out -> "Rupture"
                        ReportStockStatus.Low -> "Faible"
                        ReportStockStatus.Ok -> "OK"
                    }
                )
            }
        ),
        ReportTable(
            "risk", "Risque de rupture estimé",
            listOf("Produit", "Stock", "Vente moyenne/jour", "Jours restants", "Fiabilité"),
            st.rows
                .mapNotNull { row -> row.daysLeft?.let { row to it } }
                .sortedBy { it.second }
                .map { (row, left) ->
                    listOf<Any>(
                        row.name,
                        sectionCell(row.stock),
                        sectionCell(Math.round((row.dailyRate ?: 0.0) * 100) / 100.0),
                        sectionCell(Math.round(left * 10) / 10.0),
                        reliability
                    )
                },
            "Estimation fondée sur le rythme de vente de la période ; " +
                "elle ne tient pas compte des réapprovisionnements futurs."
        ),
        ReportTable(
            "lowrot", "Produits à faible rotation", listOf("Produit", "Stock", "Vendu (période)"),
            st.rows.filter { it.stock > 0 && it.sold == 0.0 }
                .map { listOf<Any>(it.name, sectionCell(it.stock), 0L) }
        )
    )

    return ReportSection(ReportSectionId.Stock, "Stock", kpis, tables)
}
