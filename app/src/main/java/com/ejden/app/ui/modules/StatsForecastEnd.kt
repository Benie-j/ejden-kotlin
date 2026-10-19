package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.BreakEven
import com.ejden.app.data.ForecastData
import com.ejden.app.data.Rhythm
import com.ejden.app.data.STAT_WEEKDAYS
import com.ejden.app.data.formatMoney
import com.ejden.app.data.statPct
import com.ejden.app.ui.theme.Ejden

/** Carte de chaleur : 7 jours × 12 tranches de 2 h. */
@Composable
private fun StatHeatmap(r: Rhythm) {
    val c = Ejden.colors
    val cell = RoundedCornerShape(3.dp)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Spacer(Modifier.width(22.dp))
            for (b in 0 until 12) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (b % 2 == 0) (b * 2).toString() else "",
                        color = c.textSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
        r.grid.forEachIndexed { day, cells ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(22.dp), contentAlignment = Alignment.Center) {
                    Text(STAT_WEEKDAYS[day].take(1), color = c.textSecondary, fontSize = 10.sp)
                }
                for (count in cells) {
                    val color = if (count == 0) {
                        c.border
                    } else {
                        c.primary.copy(alpha = (0.2f + 0.8f * count / r.maxCount).coerceIn(0f, 1f))
                    }
                    Box(Modifier.weight(1f).aspectRatio(1f).background(color, cell))
                }
            }
        }
    }
}

fun LazyListScope.forecastTabEnd(data: ForecastData?) {
    if (data == null) return

    /* Seuil de rentabilité */
    item {
        ModuleSection("Seuil de rentabilité") {
            when (val b = data.breakEven) {
                BreakEven.NoData ->
                    StatEmpty("Données insuffisantes : il faut des ventes avec prix d'achat connu.")

                BreakEven.NoCharges ->
                    StatEmpty("Aucune charge (loyer, transport…) enregistrée sur les 3 derniers mois complets.")

                is BreakEven.Ready -> {
                    StatLine("Charges mensuelles moyennes", formatMoney(b.monthlyCharges))
                    StatLine("Taux de marge brute", statPct(b.marginRate * 100, 1))
                    StatLine("Chiffre d'affaires minimum par mois", formatMoney(b.threshold))
                    val projected = b.projectedMonth
                    val ratio = b.ratio
                    if (projected != null && ratio != null) {
                        StatLine(
                            "Projection de ce mois",
                            "${formatMoney(projected)} (${statPct(ratio * 100)})",
                            if (ratio >= 1) LineTone.Positive else LineTone.Negative
                        )
                        StatItem(
                            "Atteinte du seuil",
                            if (ratio >= 1) "Rentable" else "Sous le seuil",
                            meter = StatMeter(
                                minOf(1.0, ratio),
                                if (ratio >= 1) StatTone.Good else if (ratio >= 0.8) StatTone.Warn else StatTone.Bad
                            )
                        )
                    }
                    StatNote("Seuil = charges mensuelles (hors marchandises) ÷ taux de marge brute. En dessous, le mois se termine à perte.")
                }
            }
        }
    }

    /* Jours et heures de pointe */
    item {
        ModuleSection("Jours et heures de pointe") {
            val r = data.rhythm
            if (r == null) {
                StatEmpty("Il faut au moins 10 ventes sur 90 jours.")
            } else {
                StatHeatmap(r)
                StatNote("Chaque case = tranche de 2 h (0 à 22 h), du lundi au dimanche. Plus la case est foncée, plus il y a de ventes.")
                StatLine("Meilleur jour", "${STAT_WEEKDAYS[r.bestDay]} · ${formatMoney(r.bestDayAverage)} en moyenne")
                StatLine("Jour le plus calme", "${STAT_WEEKDAYS[r.worstDay]} · ${formatMoney(r.worstDayAverage)} en moyenne")
                StatLine("Meilleure heure", "${r.bestHour} h – ${r.bestHour + 1} h", divider = false)
            }
        }
    }
}
