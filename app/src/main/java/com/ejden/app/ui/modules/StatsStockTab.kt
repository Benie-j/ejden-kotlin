package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.RotationLevel
import com.ejden.app.data.StockData
import com.ejden.app.data.formatMoney
import com.ejden.app.data.rotationLevel
import com.ejden.app.data.statPct
import com.ejden.app.data.statQty
import com.ejden.app.ui.theme.Ejden

@Composable
private fun StatBadgeLine(label: String, badge: String, tone: StatTone) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), color = c.textSecondary, fontSize = 14.sp)
            StatBadge(badge, tone)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

fun LazyListScope.stockTab(data: StockData?, loaded: Boolean) {
    if (!loaded) {
        item { StatEmpty("Chargement…") }
        return
    }
    if (data == null) {
        item { ModuleSection("Stock") { StatEmpty("Aucun produit enregistré.") } }
        return
    }

    /* Rotation */
    item {
        ModuleSection("Rotation du stock") {
            StatKpis(
                listOf(
                    StatKpiData(
                        "Rotation annuelle",
                        if (data.hasSales90) "${statQty(data.turnover)} ×" else "—",
                        main = true
                    ),
                    StatKpiData(
                        "Jours de stock",
                        if (data.hasSales90) "${Math.round(data.daysOfStock)} j" else "—"
                    ),
                    StatKpiData("Taux d'écoulement (30 j)", statPct(data.sellThrough))
                )
            )
            val level = rotationLevel(data.turnover)
            StatBadgeLine(
                "Rotation du stock",
                level.label,
                when (level) {
                    RotationLevel.VeryGood, RotationLevel.Good -> StatTone.Good
                    RotationLevel.Weak -> StatTone.Warn
                    RotationLevel.VeryWeak -> StatTone.Bad
                }
            )
            StatLine("Valeur du stock (prix d'achat)", formatMoney(data.stockCost))
            StatNote("Rotation = coût des produits vendus (annualisé) ÷ valeur du stock. Elle indique combien de fois le stock se renouvelle par an : au-dessus de 3, c'est sain ; en dessous de 1,5, trop d'argent dort en rayon.")
        }
    }

    /* Ruptures à prévoir */
    item {
        ModuleSection("Ruptures à prévoir") {
            if (data.risks.isEmpty()) {
                StatEmpty("Aucun produit à risque : tous couvrent plus de 14 jours de ventes.")
            } else {
                for (r in data.risks) {
                    StatItem(
                        r.name,
                        if (r.stock == 0) "En rupture" else "${maxOf(0L, Math.floor(r.cover).toLong())} j restants",
                        "Stock ${statQty(r.stock.toDouble())} · vend ~${statQty(r.velocity * 7)}/semaine · " +
                            "à commander pour 30 jours : ${statQty(r.toBuy)}",
                        StatMeter(
                            minOf(1.0, r.cover / 14),
                            if (r.cover <= 3) StatTone.Bad else if (r.cover <= 7) StatTone.Warn else StatTone.Neutral
                        )
                    )
                }
            }
            val lost = data.lostWeekly
            if (lost != null) {
                StatLine("Ventes perdues estimées par semaine", formatMoney(lost), LineTone.Negative)
            }
            StatNote("Couverture = stock actuel ÷ ventes moyennes par jour des 30 derniers jours. La quantité conseillée couvre 30 jours de ventes.")
        }
    }

    /* Dormant et sur-stock */
    item {
        ModuleSection("Stock dormant et sur-stock") {
            StatKpis(
                listOf(
                    StatKpiData("Capital immobilisé", formatMoney(data.dormantValue), main = true),
                    StatKpiData("Produits dormants", data.dormantCount.toString())
                )
            )
            for (d in data.dormant) {
                val stock = statQty(d.stock.toDouble())
                StatItem(
                    d.name,
                    formatMoney(d.value),
                    if (d.sinceDays == null) "Jamais vendu · $stock en stock"
                    else "Aucune vente depuis ${d.sinceDays} jours · $stock en stock"
                )
            }
            for (o in data.overstock) {
                StatItem(
                    o.name,
                    "${o.days} j de stock",
                    "Plus de 4 mois de ventes en rayon : inutile de recommander."
                )
            }
            StatNote("Dormant = en stock mais sans vente depuis plus de 60 jours. Pensez à une promotion ou à un lot pour récupérer cet argent.")
        }
    }
}
