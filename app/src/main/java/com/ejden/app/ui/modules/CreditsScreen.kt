package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.CREDIT_OVERDUE_DAYS
import com.ejden.app.data.Client
import com.ejden.app.data.CreditGroup
import com.ejden.app.data.CreditPayment
import com.ejden.app.data.Sale
import com.ejden.app.data.computeCreditsSummary
import com.ejden.app.data.describePayment
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.jsTrim
import com.ejden.app.data.shortDateText
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

private data class CreditsData(val sales: List<Sale>, val payments: List<CreditPayment>, val clients: List<Client>)

private fun ageLabel(days: Int): String = when {
    days <= 0 -> "aujourd'hui"
    days == 1 -> "il y a 1 jour"
    else -> "il y a $days jours"
}

@Composable
fun CreditsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val zone = remember { ZoneId.systemDefault() }

    var data by remember { mutableStateOf(CreditsData(emptyList(), emptyList(), emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            CreditsData(app.sales.getSales(), app.credits.getCreditPayments(), app.clients.getClients())
        }
        loaded = true
    }

    val summary = remember(data) { computeCreditsSummary(data.sales, data.payments, data.clients) }

    var query by rememberSaveable { mutableStateOf("") }
    var overdueOnly by rememberSaveable { mutableStateOf(false) }
    // Fiche ouverte : elle reste ouverte après un encaissement.
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }

    val search = query.jsTrim().lowercase()
    val groups = remember(summary, overdueOnly, search) {
        summary.groups.filter { (!overdueOnly || it.overdue) && (search.isEmpty() || it.name.lowercase().contains(search)) }
    }

    ModuleScaffold("FINANCES", "Crédits", { nav.goBack() }, toast) {
        item {
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "À encaisser", formatNumber(summary.total), Modifier.fillMaxWidth(),
                    unit = "FCFA · à encaisser", wide = true
                )
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Débiteurs", summary.debtors.toString(), Modifier.weight(1f).fillMaxHeight())
                    SummaryCard(
                        "En retard (+30 jours)", formatNumber(summary.overdueAmount), Modifier.weight(1f).fillMaxHeight(),
                        unit = "FCFA", valueColor = c.error
                    )
                }
            }
        }

        item { ModuleSearch(query, { query = it }, "Rechercher un débiteur...") }

        item {
            Row(Modifier.padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Tous", !overdueOnly) { overdueOnly = false }
                FilterPill("En retard", overdueOnly) { overdueOnly = true }
            }
        }

        if (loaded && groups.isEmpty()) {
            item {
                val none = summary.groups.isEmpty()
                ModuleEmpty(
                    if (none) "Aucun crédit en cours" else "Aucun résultat",
                    when {
                        none -> "Les ventes à crédit non soldées apparaîtront ici."
                        overdueOnly && search.isEmpty() -> "Aucun crédit de plus de $CREDIT_OVERDUE_DAYS jours."
                        else -> "Aucun débiteur ne correspond à votre recherche."
                    }
                )
            }
        }

        items(groups, key = { it.key }) { group ->
            CreditCard(
                group = group,
                zone = zone,
                expanded = openKey == group.key,
                onToggle = { openKey = if (openKey == group.key) null else group.key },
                onPay = { amount, method ->
                    val outcome = describePayment(
                        app.credits.recordCreditPayment(group.sales.map { it.sale.id }, amount.toDouble(), method),
                        amount.toDouble(), group.amount
                    )
                    toast.show(outcome.message)
                    if (outcome.success) {
                        openKey = group.key
                        reloadKey += 1
                    }
                    outcome.success
                },
                onError = { toast.show(it) },
                onClient = { nav.open(Routes.module("clients")) }
            )
        }
    }
}

@Composable
private fun CreditCard(
    group: CreditGroup,
    zone: ZoneId,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPay: (Int, com.ejden.app.data.PaymentMethod) -> Boolean,
    onError: (String) -> Unit,
    onClient: () -> Unit
) {
    val c = Ejden.colors
    val count = group.sales.size

    RowCard(
        expanded = expanded,
        onToggle = onToggle,
        summary = {
            RowMain(
                group.name,
                "$count vente" + (if (count > 1) "s" else "") + " · la plus ancienne " + ageLabel(group.oldestDays),
                Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatMoney(group.amount), color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (group.overdue) Badge("En retard", BadgeTone.Overdue) else Badge("À encaisser", BadgeTone.Debt)
            }
        },
        details = {
            ModuleDetailTitle("Ventes à encaisser")
            for (entry in group.sales) {
                ModuleDetailRow(
                    shortDateText(entry.sale.createdAt, zone) + " · total " + formatMoney(entry.sale.total),
                    "Reste " + formatMoney(entry.remaining),
                    valueColor = c.error
                )
            }

            // Encaissement : réparti sur les ventes, la plus ancienne d'abord.
            PaymentForm(due = group.amount, onPay = onPay, onError = onError)

            if (group.clientId != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 46.dp)
                        .background(c.surface, RoundedCornerShape(10.dp))
                        .border(1.dp, c.border, RoundedCornerShape(10.dp))
                        .tap(onClient),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Voir la fiche client", color = c.primary, fontSize = 14.sp, fontWeight = FontWeight(600))
                }
            }
        }
    )
}
