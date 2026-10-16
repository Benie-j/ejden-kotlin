package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.EjdenApp
import com.ejden.app.data.CancelPurchaseResult
import com.ejden.app.data.Purchase
import com.ejden.app.data.PurchaseItem
import com.ejden.app.data.SupplierPayment
import com.ejden.app.data.computePurchaseRemainingMap
import com.ejden.app.data.formatMoney
import com.ejden.app.data.groupByDay
import com.ejden.app.data.parseDateMillis
import com.ejden.app.data.shortDateText
import com.ejden.app.data.timeText
import com.ejden.app.ui.theme.Ejden
import java.time.LocalDate
import java.time.ZoneId

private sealed class PurchaseRow(val key: String) {
    class Day(val label: String, key: String) : PurchaseRow(key)
    class Item(val purchase: Purchase, val remaining: Double, key: String) : PurchaseRow(key)
}

/** Historique des achats : titres de jour, cartes dépliables, annulation. */
internal fun LazyListScope.purchaseHistory(
    purchases: List<Purchase>,
    payments: List<SupplierPayment>,
    loaded: Boolean,
    toast: ModuleToastState,
    onChanged: () -> Unit
) {
    if (loaded && purchases.isEmpty()) {
        item { ModuleEmpty("Aucun achat", "Les achats enregistrés apparaîtront ici.") }
    }

    val zone = ZoneId.systemDefault()
    val remaining = computePurchaseRemainingMap(purchases, payments)
    val sorted = purchases.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }

    val rows = ArrayList<PurchaseRow>()
    var index = 0
    for (group in groupByDay(sorted, LocalDate.now(), zone) { it.createdAt }) {
        rows.add(PurchaseRow.Day(group.label, "day:" + index))
        index += 1
        for (purchase in group.items) {
            rows.add(PurchaseRow.Item(purchase, remaining[purchase.id] ?: 0.0, "purchase:" + index + ":" + purchase.id))
            index += 1
        }
    }

    items(rows, key = { it.key }) { row ->
        when (row) {
            is PurchaseRow.Day -> DayHeading(row.label)
            is PurchaseRow.Item -> PurchaseCard(row.purchase, row.remaining, zone, toast, onChanged)
        }
    }
}

@Composable
private fun PurchaseCard(
    purchase: Purchase,
    remaining: Double,
    zone: ZoneId,
    toast: ModuleToastState,
    onChanged: () -> Unit
) {
    val c = Ejden.colors
    val app = LocalContext.current.applicationContext as EjdenApp
    val cancelled = purchase.isCancelled
    var expanded by rememberSaveable(purchase.id) { mutableStateOf(false) }

    val count = purchase.items.sumOf { it.quantity }

    RowCard(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        summary = {
            RowMain(
                purchase.supplierName,
                timeText(purchase.createdAt, zone) + " · " + count + " article" + (if (count > 1) "s" else ""),
                Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(purchase.total),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Box(Modifier.height(4.dp))
                if (cancelled) {
                    Badge("Annulé", BadgeTone.Overdue)
                } else if (remaining > 0.0) {
                    Badge("Reste " + formatMoney(remaining), BadgeTone.Debt)
                } else {
                    Badge("Payé", BadgeTone.Success)
                }
            }
        },
        details = {
            ModuleDetailTitle("Produits")
            for (item in purchase.items) {
                ItemLine(item)
            }

            ModuleDetailTitle("Paiement")
            ModuleDetailRow("Total", formatMoney(purchase.total))
            ModuleDetailRow("Payé à la livraison", formatMoney(purchase.paid))
            if (!cancelled && remaining > 0.0) {
                ModuleDetailRow("Reste à payer", formatMoney(remaining), valueColor = c.error)
            }
            if (purchase.note.isNotEmpty()) {
                ModuleDetailRow("Note", purchase.note)
            }

            val cancelledAt = purchase.cancelledAt
            if (cancelledAt != null) {
                ModuleDetailRow(
                    "Annulé le",
                    shortDateText(cancelledAt, zone) + " " + timeText(cancelledAt, zone)
                )
            } else {
                Column(Modifier.padding(top = 14.dp)) {
                    DeleteControl(
                        label = "Annuler cet achat",
                        question = "Annuler cet achat ? Le stock livré sera retiré (s'il est encore en rayon) et " +
                            "l'argent payé à la livraison sera retiré des sorties de caisse. " +
                            "Les prix d'achat mis à jour ne reviennent pas en arrière."
                    ) {
                        when (val result = app.purchases.cancelPurchase(purchase.id)) {
                            is CancelPurchaseResult.Ok -> toast.show(
                                if (result.missingProducts > 0) {
                                    "Achat annulé. Certains produits supprimés n'ont pas pu être mis à jour."
                                } else {
                                    "Achat annulé. Le stock a été remis comme avant."
                                }
                            )
                            is CancelPurchaseResult.StockUsed -> toast.show(
                                "Impossible : le stock de « " + result.productName +
                                    " » est déjà entamé (ventes ou sorties)."
                            )
                            CancelPurchaseResult.NotFound -> toast.show("Achat introuvable.")
                            CancelPurchaseResult.AlreadyCancelled -> toast.show("Cet achat est déjà annulé.")
                            CancelPurchaseResult.HasPayments -> toast.show(
                                "Cet achat a des paiements : annulez-les d'abord (page Fournisseurs)."
                            )
                            CancelPurchaseResult.StorageError -> toast.show("Impossible d'annuler l'achat.")
                        }
                        onChanged()
                    }
                }
            }
        }
    )
}

/** Une ligne de produit d'un achat : nom, « 24 × 450 FCFA » et total. */
@Composable
private fun ItemLine(item: PurchaseItem) {
    val c = Ejden.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.productName, color = c.text, fontSize = 13.sp)
            Text(
                item.quantity.toString() + " × " + formatMoney(item.unitCost),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 11.sp
            )
        }
        Text(
            formatMoney(item.quantity * item.unitCost),
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}
