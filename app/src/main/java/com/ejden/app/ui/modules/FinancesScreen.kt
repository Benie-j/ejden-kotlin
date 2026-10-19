package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.ExpenseCategory
import com.ejden.app.data.FinanceFigures
import com.ejden.app.data.FinanceReport
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.PeriodChoice
import com.ejden.app.data.PeriodPreset
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.isValidDay
import com.ejden.app.data.percentChange
import com.ejden.app.data.resolvePeriod
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private val FINANCE_PRESETS = listOf(
    PeriodPreset.Today, PeriodPreset.Days7, PeriodPreset.Month1,
    PeriodPreset.Months3, PeriodPreset.Months6, PeriodPreset.Custom
)

private fun financePresetLabel(preset: PeriodPreset): String = when (preset) {
    PeriodPreset.Today -> "Aujourd'hui"
    PeriodPreset.Days7 -> "7 derniers jours"
    PeriodPreset.Month1 -> "1 mois"
    PeriodPreset.Months3 -> "3 mois"
    PeriodPreset.Months6 -> "6 mois"
    PeriodPreset.Custom -> "Personnalisée"
}

/** Libellés propres à Finances (identiques à la version HTML). */
private fun financeMethodLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.Cash -> "Espèces"
    PaymentMethod.MobileMoney -> "Mobile money"
    PaymentMethod.Card -> "Carte"
    PaymentMethod.Other -> "Autre"
    PaymentMethod.Credit -> "À crédit"
}

private fun financePercent(value: Double): String = Math.round(value).toString() + " %"

private fun financePlural(n: Int, word: String): String = if (n > 1) word + "s" else word

private fun financeQuantity(value: Double): String =
    if (value == Math.floor(value)) {
        formatNumber(value)
    } else {
        (Math.round(value * 1000) / 1000.0).toString().replace('.', ',')
    }

@Composable
fun FinancesScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()

    var period by remember { mutableStateOf(app.financePeriod.get()) }
    var customOpen by remember { mutableStateOf(period.preset == PeriodPreset.Custom) }
    var fromDay by remember { mutableStateOf(period.from ?: "") }
    var toDay by remember { mutableStateOf(period.to ?: "") }
    var pickerFor by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<FinanceReport?>(null) }

    LaunchedEffect(resumeKey, period) {
        report = withContext(Dispatchers.Default) {
            app.finance.load(resolvePeriod(period))
        }
    }

    fun selectPreset(preset: PeriodPreset) {
        if (preset == PeriodPreset.Custom) {
            val today = LocalDate.now().toString()
            val from = period.from ?: today
            val to = period.to ?: today
            fromDay = from
            toDay = to
            customOpen = true
            period = PeriodChoice(PeriodPreset.Custom, from, to)
        } else {
            customOpen = false
            period = PeriodChoice(preset)
        }
        app.financePeriod.save(period)
    }

    fun applyCustom() {
        if (!isValidDay(fromDay) || !isValidDay(toDay)) {
            toast.show("Choisissez deux dates valides.")
            return
        }
        if (fromDay > toDay) {
            toast.show("La date de début doit précéder la date de fin.")
            return
        }
        period = PeriodChoice(PeriodPreset.Custom, fromDay, toDay)
        app.financePeriod.save(period)
    }

    val resolved = remember(period) { resolvePeriod(period) }
    val caption = if (period.preset == PeriodPreset.Custom) {
        resolved.buttonLabel
    } else {
        financePresetLabel(period.preset) + " · comparé à la " +
            (if (resolved.comparedTo == "hier") "veille" else "période précédente")
    }

    val r = report

    ModuleScaffold("FINANCES", "Vue d'ensemble", { nav.goBack() }, toast, listState) {
        item {
            Row(
                Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (preset in FINANCE_PRESETS) {
                    FinanceChip(financePresetLabel(preset), preset == period.preset) { selectPreset(preset) }
                }
            }
        }

        if (customOpen) {
            item {
                FinanceBox {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            ModuleField("Du") { DocDateField(fromDay, false) { pickerFor = "from" } }
                        }
                        Box(Modifier.weight(1f)) {
                            ModuleField("Au") { DocDateField(toDay, false) { pickerFor = "to" } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Appliquer", Modifier.fillMaxWidth()) { applyCustom() }
                }
            }
        }

        item {
            Text(
                caption,
                modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
                color = Ejden.colors.textSecondary,
                fontSize = 12.sp
            )
        }

        if (r != null) {
            val cur = r.current
            val prev = r.previous

            item { ResultCard(cur, prev, r.comparedTo) }

            item {
                val margin = cur.margin
                Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiCard("Chiffre d'affaires", formatMoney(cur.revenue), Modifier.weight(1f).fillMaxHeight()) {
                            DeltaBadge(cur.revenue, prev.revenue, r.comparedTo, true)
                        }
                        KpiCard(
                            "Marge brute",
                            if (margin == null) "—" else formatMoney(margin),
                            Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (margin != null && cur.costedRevenue > 0.0) {
                                KpiNote("Taux de marge " + financePercent(margin / cur.costedRevenue * 100))
                            }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KpiCard("Charges", formatMoney(cur.charges), Modifier.weight(1f).fillMaxHeight()) {
                            DeltaBadge(cur.charges, prev.charges, r.comparedTo, false)
                        }
                        KpiCard("Ventes", cur.salesCount.toString(), Modifier.weight(1f).fillMaxHeight()) {
                            if (cur.salesCount > 0) {
                                KpiNote("Panier moyen " + formatMoney(cur.revenue / cur.salesCount))
                            }
                        }
                    }
                }
            }

            if (r.alerts.isNotEmpty()) {
                item {
                    ModuleSection("Points d'attention") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (alert in r.alerts) {
                                val c = Ejden.colors
                                Text(
                                    alert.text,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (alert.danger) c.errorSoft else c.warningSoft, RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    color = c.text,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                val net = r.cashFlow.cashIn - r.cashFlow.cashOut
                ModuleSection("Trésorerie de la période") {
                    MetricRow("Entrées d'argent", formatMoney(r.cashFlow.cashIn))
                    MetricRow("Sorties d'argent", formatMoney(r.cashFlow.cashOut))
                    MetricRow(
                        "Variation de la caisse",
                        (if (net > 0.0) "+" else "") + formatMoney(net),
                        tone = if (net >= 0.0) "positive" else "negative",
                        total = true,
                        last = true
                    )
                }
            }

            if (r.collected.count > 0 || r.receivable.count > 0) {
                item {
                    ModuleSection("Factures") {
                        MetricRow(
                            "Encaissé sur la période", formatMoney(r.collected.amount),
                            note = r.collected.count.toString() + " " + financePlural(r.collected.count, "paiement"),
                            last = r.receivable.count == 0 && r.receivable.overdueCount == 0
                        )
                        MetricRow(
                            "Reste à encaisser", formatMoney(r.receivable.total),
                            note = r.receivable.count.toString() + " " + financePlural(r.receivable.count, "facture"),
                            last = r.receivable.overdueCount == 0
                        )
                        if (r.receivable.overdueCount > 0) {
                            MetricRow(
                                "Dont en retard", formatMoney(r.receivable.overdueTotal),
                                note = r.receivable.overdueCount.toString() + " " +
                                    financePlural(r.receivable.overdueCount, "facture"),
                                tone = "negative",
                                last = true
                            )
                        }
                    }
                }
            }

            item {
                val p = r.position
                ModuleSection("Situation actuelle") {
                    MetricRow("Trésorerie (caisse)", formatMoney(p.cash))
                    MetricRow(
                        "Crédits à encaisser", formatMoney(p.credits),
                        note = if (p.creditDebtors > 0) {
                            p.creditDebtors.toString() + " " + financePlural(p.creditDebtors, "client")
                        } else {
                            null
                        }
                    )
                    MetricRow(
                        "Factures à encaisser", formatMoney(p.invoices),
                        note = if (p.invoicesCount > 0) {
                            p.invoicesCount.toString() + " " + financePlural(p.invoicesCount, "facture")
                        } else {
                            null
                        }
                    )
                    MetricRow(
                        "Stock (au prix d'achat)", formatMoney(p.stockCost),
                        note = "Valeur de vente : " + formatMoney(p.stockRetail)
                    )
                    MetricRow("Dettes fournisseurs", "− " + formatMoney(p.supplierDebt))
                    MetricRow(
                        "Situation nette estimée", formatMoney(p.equity),
                        tone = if (p.equity >= 0.0) "positive" else "negative",
                        total = true
                    )
                    FinanceNote(
                        "Situation nette = caisse + crédits et factures à encaisser + stock au prix d'achat − dettes fournisseurs.",
                        11.sp
                    )
                }
            }

            item { ExpensesSection(cur) }
            item { MethodsSection(cur) }
            item { TopProductsSection(cur) }
        }
    }

    if (pickerFor != null) {
        DocDayPickerDialog(
            initialDay = (if (pickerFor == "from") fromDay else toDay).ifEmpty { LocalDate.now().toString() },
            onPick = { if (pickerFor == "from") fromDay = it else toDay = it },
            onDismiss = { pickerFor = null }
        )
    }
}

@Composable
private fun ExpensesSection(current: FinanceFigures) {
    val entries = current.expensesByCategory.entries.sortedByDescending { it.value }
    val total = entries.sumOf { it.value }

    ModuleSection("Dépenses par catégorie") {
        if (total <= 0.0) {
            FinanceNote("Aucune dépense sur cette période.", 11.sp, top = 0.dp)
        } else {
            entries.forEachIndexed { index, entry ->
                BarRow(
                    label = entry.key.label,
                    value = formatMoney(entry.value) + " · " + financePercent(entry.value / total * 100),
                    ratio = entry.value / total,
                    warning = entry.key == ExpenseCategory.Merchandise,
                    last = index == entries.size - 1
                )
            }
            if (current.goodsExpenses > 0.0) {
                FinanceNote(
                    "Les achats de marchandises ne sont pas comptés dans les charges : leur coût est déjà déduit dans la marge, au moment de la vente.",
                    11.sp
                )
            }
        }
    }
}

@Composable
private fun MethodsSection(current: FinanceFigures) {
    ModuleSection("Ventes par mode de paiement") {
        if (current.revenue <= 0.0) {
            FinanceNote("Aucune vente sur cette période.", 11.sp, top = 0.dp)
        } else {
            val entries = current.byMethod.entries.sortedByDescending { it.value.amount }
            entries.forEachIndexed { index, entry ->
                BarRow(
                    label = financeMethodLabel(entry.key),
                    value = formatMoney(entry.value.amount) + " · " +
                        financePercent(entry.value.amount / current.revenue * 100),
                    ratio = entry.value.amount / current.revenue,
                    warning = entry.key == PaymentMethod.Credit,
                    last = index == entries.size - 1
                )
            }
        }
    }
}

@Composable
private fun TopProductsSection(current: FinanceFigures) {
    val c = Ejden.colors

    ModuleSection("Produits les plus rentables") {
        if (current.products.isEmpty()) {
            FinanceNote("Pas encore de marge calculable sur cette période.", 11.sp, top = 0.dp)
        } else {
            current.products.forEachIndexed { index, product ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier.size(24.dp).background(c.primarySoft, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((index + 1).toString(), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(product.name, color = c.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            financeQuantity(product.quantity) + " vendu" + (if (product.quantity > 1) "s" else ""),
                            color = c.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Text(formatMoney(product.margin), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
            FinanceNote("Marge avant remise globale de la vente.", 11.sp)
        }
    }
}

@Composable
private fun ResultCard(current: FinanceFigures, previous: FinanceFigures, comparedTo: String) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    val result = current.netResult()

    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(18.dp)
    ) {
        Text(
            "RÉSULTAT NET ESTIMÉ",
            color = c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )

        if (result == null) {
            Text(
                "—",
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                color = c.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            FinanceNote(
                "Aucune vente de cette période n'a de prix d'achat connu : la marge, et donc le résultat, ne peuvent pas être calculés."
            )
        } else {
            Text(
                formatMoney(result),
                modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                color = if (result >= 0.0) c.success else c.error,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )

            val previousResult = previous.netResult()
            if (previousResult != null) DeltaBadge(result, previousResult, comparedTo, true)

            FinanceNote("Marge brute " + formatMoney(current.margin ?: 0.0) + " − charges " + formatMoney(current.charges))

            if (current.revenue > 0.0 && current.costedRevenue < current.revenue) {
                FinanceNote(
                    "Calcul basé sur " + financePercent(current.costedRevenue / current.revenue * 100) +
                        " du chiffre d'affaires : les autres ventes n'ont pas de prix d'achat enregistré."
                )
            }
        }
    }
}

@Composable
private fun DeltaBadge(current: Double, previous: Double, comparedTo: String, goodWhenUp: Boolean) {
    val change = percentChange(current, previous) ?: return
    val c = Ejden.colors
    val sign = if (change > 0) "+" else ""
    val background = when {
        change == 0 -> c.primarySoft
        (change > 0) == goodWhenUp -> c.successSoft
        else -> c.errorSoft
    }
    val color = when {
        change == 0 -> c.textSecondary
        (change > 0) == goodWhenUp -> c.success
        else -> c.error
    }

    Text(
        sign + change + " % vs " + comparedTo,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(background, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    modifier: Modifier,
    extra: @Composable ColumnScope.() -> Unit = {}
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)

    Column(modifier.background(c.surface, shape).border(1.dp, c.border, shape).padding(14.dp)) {
        Text(label, color = c.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        extra()
    }
}

@Composable
private fun KpiNote(text: String) {
    Text(text, modifier = Modifier.padding(top = 4.dp), color = Ejden.colors.textSecondary, fontSize = 11.sp)
}

@Composable
private fun FinanceNote(text: String, size: androidx.compose.ui.unit.TextUnit = 12.sp, top: androidx.compose.ui.unit.Dp = 6.dp) {
    Text(
        text,
        modifier = Modifier.padding(top = top),
        color = Ejden.colors.textSecondary,
        fontSize = size,
        lineHeight = (size.value * 1.45f).sp
    )
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    note: String? = null,
    tone: String? = null,
    total: Boolean = false,
    last: Boolean = false
) {
    val c = Ejden.colors
    val valueColor = when (tone) {
        "positive" -> c.success
        "negative" -> c.error
        else -> c.text
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = c.textSecondary, fontSize = 13.sp)
                if (note != null) {
                    Text(note, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 11.sp)
                }
            }
            Text(
                value,
                color = valueColor,
                fontSize = if (total) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun BarRow(label: String, value: String, ratio: Double, warning: Boolean, last: Boolean) {
    val c = Ejden.colors
    val fraction = maxOf(0.02, minOf(1.0, ratio)).toFloat()

    Column(Modifier.fillMaxWidth().padding(bottom = if (last) 0.dp else 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(label, modifier = Modifier.weight(1f), color = c.text, fontSize = 13.sp)
            Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.primarySoft)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(8.dp)
                    .background(if (warning) c.warning else c.primary, CircleShape)
            )
        }
    }
}

@Composable
private fun FinanceChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .height(38.dp)
            .background(if (active) c.primary else c.surface, CircleShape)
            .border(1.dp, if (active) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** Encadré sans titre (panneau « Période personnalisée »). */
@Composable
private fun FinanceBox(content: @Composable ColumnScope.() -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .padding(bottom = 14.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .padding(16.dp)
    ) {
        content()
    }
}
