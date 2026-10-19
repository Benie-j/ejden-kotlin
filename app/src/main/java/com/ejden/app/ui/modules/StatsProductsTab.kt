package com.ejden.app.ui.modules

import androidx.compose.foundation.lazy.LazyListScope
import com.ejden.app.data.AbcClass
import com.ejden.app.data.ProductMove
import com.ejden.app.data.ProductsData
import com.ejden.app.data.formatMoney
import com.ejden.app.data.statPct
import com.ejden.app.data.statQty
import com.ejden.app.data.statSignedPct

private fun plural(count: Int): String = if (count > 1) "s" else ""

private fun movesDetail(m: ProductMove): String = "${statQty(m.before)} → ${statQty(m.now)} unités"

fun LazyListScope.productsTab(data: ProductsData?, loaded: Boolean) {
    if (!loaded) {
        item { StatEmpty("Chargement…") }
        return
    }
    if (data == null) {
        item { ModuleSection("Produits") { StatEmpty("Aucune vente sur cette période.") } }
        return
    }

    /* ABC (Pareto) */
    val abc = data.abc
    fun classTitle(letter: String, k: AbcClass) = "Classe $letter · ${k.count} produit${plural(k.count)}"
    item {
        ModuleSection("Analyse ABC (Pareto)") {
            StatItem(
                classTitle("A", abc.a), statPct(abc.a.sharePct),
                "Vos produits stars : ne jamais être en rupture.",
                StatMeter(abc.a.sharePct / 100, StatTone.Good)
            )
            StatItem(
                classTitle("B", abc.b), statPct(abc.b.sharePct),
                "Ventes régulières : suivi normal.",
                StatMeter(abc.b.sharePct / 100, StatTone.Warn)
            )
            StatItem(
                classTitle("C", abc.c), statPct(abc.c.sharePct),
                "Faible contribution : limiter le stock.",
                StatMeter(abc.c.sharePct / 100)
            )
            StatLine("Produits du catalogue sans aucune vente", abc.unsold.toString())
            StatNote("Méthode : produits triés par chiffre d'affaires ; A = jusqu'à 80 % cumulés, B = jusqu'à 95 %, C = le reste.")
            abc.topA.forEachIndexed { index, t ->
                StatLine(
                    "${t.name} (A)",
                    "${formatMoney(t.amount)} · ${statPct(t.sharePct, 1)}",
                    divider = index != abc.topA.lastIndex
                )
            }
        }
    }

    /* Catégories */
    if (data.categories.isNotEmpty()) {
        item {
            ModuleSection("Chiffre d'affaires par catégorie") {
                data.categories.forEachIndexed { index, cat ->
                    StatItem(
                        cat.name, formatMoney(cat.amount), statPct(cat.sharePct, 1),
                        StatMeter(cat.ratio),
                        divider = index != data.categories.lastIndex
                    )
                }
            }
        }
    }

    /* Hausses et baisses */
    if (data.risers.isNotEmpty() || data.fallers.isNotEmpty()) {
        item {
            ModuleSection("Produits en hausse / en baisse") {
                for (m in data.risers) StatItem(m.name, statSignedPct(m.change), movesDetail(m))
                for (m in data.fallers) StatItem(m.name, statSignedPct(m.change), movesDetail(m))
                StatNote("Comparaison des quantités vendues avec la période précédente de même durée.")
            }
        }
    }

    /* Prix à revoir */
    if (data.lossMakers.isNotEmpty() || data.thin.isNotEmpty()) {
        item {
            ModuleSection("Prix à revoir") {
                for (p in data.lossMakers) {
                    StatItem(
                        p.name, "Vendu à perte",
                        "Achat ${formatMoney(p.purchasePrice)} · vente ${formatMoney(p.salePrice)}"
                    )
                }
                for (p in data.thin) {
                    StatItem(
                        p.name, "Marge ${statPct(p.marginPct, 1)}",
                        "Achat ${formatMoney(p.purchasePrice)} · vente ${formatMoney(p.salePrice)}"
                    )
                }
                StatNote("Produits dont le prix de vente est inférieur au prix d'achat ou laisse moins de 10 % de marge.")
            }
        }
    }
}
