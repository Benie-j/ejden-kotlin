package com.ejden.app.ui.modules

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import com.ejden.app.data.ForecastData
import com.ejden.app.data.STAT_MONTHS
import com.ejden.app.data.formatMoney
import com.ejden.app.data.statSignedPct

@Composable
private fun StatPctLine(label: String, pct: Double?) {
    StatLine(
        label,
        if (pct != null) statSignedPct(pct) else "—",
        if (pct != null && pct >= 0) LineTone.Positive else LineTone.Negative
    )
}

fun LazyListScope.forecastTab(data: ForecastData?, loaded: Boolean) {
    if (!loaded) {
        item { StatEmpty("Chargement…") }
        return
    }
    if (data == null) {
        item {
            ModuleSection("Prévisions") {
                StatEmpty("Aucune vente enregistrée : les prévisions apparaîtront dès les premières ventes.")
            }
        }
        return
    }

    /* Court terme */
    val s = data.shortTerm
    item {
        ModuleSection("Prévision à court terme") {
            if (s.daysCovered >= 14) {
                StatKpis(
                    listOf(
                        StatKpiData("7 prochains jours", formatMoney(s.next7)),
                        StatKpiData("30 prochains jours", formatMoney(s.next30)),
                        StatKpiData("Fin ${STAT_MONTHS[s.monthIndex]} (projetée)", formatMoney(s.projectedMonth), main = true)
                    )
                )
                StatLine("Réalisé ce mois-ci", formatMoney(s.realized))
                StatLine("Encore attendu d'ici la fin du mois", formatMoney(s.remaining))
                StatNote("Méthode : moyenne des ventes de chaque jour de la semaine sur les 8 dernières semaines, appliquée aux jours à venir.")
            } else {
                StatEmpty("Il faut au moins 14 jours d'historique (actuellement ${maxOf(0, s.daysCovered)}).")
            }
        }
    }

    /* Semaines */
    val w = data.weekly
    item {
        ModuleSection("Ventes par semaine") {
            if (w != null) {
                StatBarChart(w.chart.labels, w.chart.values, w.chart.forecast, w.chart.forecastLabels, w.chart.sd)
                StatLegend()
                StatTrendLine("Tendance", w.trend)
                StatLine("Prévision des 4 prochaines semaines", formatMoney(w.forecastTotal))
                StatNote("Méthode : droite de tendance (régression linéaire) sur les semaines complètes ; la fourchette correspond à l'écart habituel autour de cette droite.")
            } else {
                StatEmpty("Il faut au moins 4 semaines complètes de ventes pour tracer une tendance.")
            }
        }
    }

    /* Mois et croissance */
    val m = data.monthly
    item {
        ModuleSection("Ventes par mois et croissance") {
            if (m != null) {
                StatBarChart(m.chart.labels, m.chart.values, m.chart.forecast, m.chart.forecastLabels, m.chart.sd)
                StatLegend()
                StatPctLine("Dernier mois complet vs précédent", m.lastVsPrevPct)
                if (m.recent3VsBefore3Pct != null) StatPctLine("3 derniers mois vs 3 mois d'avant", m.recent3VsBefore3Pct)
                if (m.yearOnYearPct != null) StatPctLine("Même mois, il y a un an", m.yearOnYearPct)
                StatLine("Meilleur mois", "${m.bestLabel} · ${formatMoney(m.best)}")
                StatLine("Mois le plus faible", "${m.worstLabel} · ${formatMoney(m.worst)}")
                StatLine("Prévision des 3 prochains mois", formatMoney(m.forecastTotal))
                StatNote("Avec moins de 24 mois d'historique, la saisonnalité annuelle ne peut pas être mesurée : la prévision suit uniquement la tendance.")
            } else {
                StatEmpty("Il faut au moins 3 mois complets de ventes.")
            }
        }
    }
}
