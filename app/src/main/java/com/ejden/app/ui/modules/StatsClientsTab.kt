package com.ejden.app.ui.modules

import androidx.compose.foundation.lazy.LazyListScope
import com.ejden.app.data.ClientStats
import com.ejden.app.data.formatMoney
import com.ejden.app.data.statPct
import com.ejden.app.data.statQty

private fun purchases(count: Int): String = "$count achat${if (count > 1) "s" else ""}"

fun LazyListScope.clientsTab(data: ClientStats?, loaded: Boolean) {
    if (!loaded) {
        item { StatEmpty("Chargement…") }
        return
    }
    if (data == null) {
        item { ModuleSection("Clients et paniers") { StatEmpty("Aucune vente sur cette période.") } }
        return
    }

    /* Paniers */
    val b = data.baskets
    item {
        ModuleSection("Paniers") {
            StatKpis(
                listOf(
                    StatKpiData("Panier moyen", formatMoney(b.mean), main = true),
                    StatKpiData("Panier médian", formatMoney(b.median)),
                    StatKpiData("Articles par vente", statQty(b.itemsPerSale)),
                    StatKpiData("Vente la plus élevée", formatMoney(b.maxSale))
                )
            )
            StatLine(
                "Part des ventes à crédit",
                statPct(b.creditSharePct, 1),
                if (b.creditSharePct > 30) LineTone.Negative else LineTone.None
            )
            StatNote("Le panier médian est moins sensible aux gros achats isolés que la moyenne.")
        }
    }

    /* Produits achetés ensemble */
    val pairs = data.pairs
    item {
        ModuleSection("Produits souvent achetés ensemble") {
            if (pairs.top.isEmpty()) {
                StatEmpty("Pas encore assez de paniers à plusieurs produits pour détecter des associations.")
            } else {
                for (p in pairs.top) {
                    StatItem(
                        "${p.a} + ${p.b}",
                        "${p.count} fois",
                        "${statPct(p.sharePct, 1)} des ventes",
                        StatMeter(p.count.toDouble() / pairs.top[0].count)
                    )
                }
            }
            StatLine("Ventes avec plusieurs produits", statPct(pairs.multiSharePct))
            StatNote("Idée : placez ces produits côte à côte ou proposez-les en lot.")
        }
    }

    /* Clients identifiés */
    val id = data.identified
    if (id == null) {
        item {
            ModuleSection("Clients identifiés") {
                StatEmpty("Aucune vente rattachée à une fiche client sur cette période.")
                StatNote("Choisissez un client au moment de la vente pour suivre sa fidélité et son historique d'achats.")
            }
        }
        return
    }

    item {
        ModuleSection("Clients identifiés") {
            StatKpis(
                listOf(
                    StatKpiData("Clients actifs", id.active.toString(), main = true),
                    StatKpiData("Nouveaux", id.fresh.toString()),
                    StatKpiData("Fidèles (revenus)", id.returning.toString()),
                    StatKpiData("Taux de réachat", statPct(id.repeatPct))
                )
            )
            StatLine("Ventes rattachées à un client", statPct(id.identifiedPct))
            StatNote("Taux de réachat = part des clients actifs ayant acheté au moins 2 fois sur la période.")
        }
    }

    item {
        ModuleSection("Meilleurs clients") {
            for (c in id.top) {
                StatItem(
                    c.name,
                    formatMoney(c.amount),
                    "${purchases(c.count)} · panier moyen ${formatMoney(c.amount / c.count)}",
                    StatMeter(c.amount / id.top[0].amount)
                )
            }
            StatLine(
                "Poids des 5 meilleurs dans les ventes identifiées",
                statPct(id.topSharePct),
                divider = false
            )
        }
    }

    if (id.sleeping.isNotEmpty()) {
        item {
            ModuleSection("Clients à relancer") {
                for (c in id.sleeping) {
                    StatItem(c.name, "${c.daysSince} j sans achat", "${c.count} achats au total")
                }
                StatNote("Clients qui achetaient régulièrement mais ne sont pas revenus depuis plus de 60 jours.")
            }
        }
    }
}
