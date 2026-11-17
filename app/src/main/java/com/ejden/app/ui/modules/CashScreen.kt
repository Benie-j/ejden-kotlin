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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.CashEntry
import com.ejden.app.data.CashFormResult
import com.ejden.app.data.CashMovement
import com.ejden.app.data.CashSummary
import com.ejden.app.data.CashType
import com.ejden.app.data.MoneyField
import com.ejden.app.data.cashMethodLabel
import com.ejden.app.data.cashMovementSubtitle
import com.ejden.app.data.cashSavedMessage
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.groupByDay
import com.ejden.app.data.newId
import com.ejden.app.data.toIso
import com.ejden.app.data.validateCashForm
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed class CashRow(val key: String) {
    class Day(val label: String, key: String) : CashRow(key)
    class Item(val movement: CashMovement, key: String) : CashRow(key)
}

@Composable
fun CashScreen(nav: NavController, requested: String?) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val zone = remember { ZoneId.systemDefault() }

    var summary by remember { mutableStateOf(CashSummary(0.0, 0.0, 0.0, emptyMap(), emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        summary = withContext(Dispatchers.Default) { app.cash.getCashSummary() }
        loaded = true
    }

    // Venu de « Entrée de caisse » : le montant est prêt à être saisi.
    var isIn by rememberSaveable { mutableStateOf(requested != "out") }
    var amount by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var amountInvalid by remember { mutableStateOf(false) }
    var reasonInvalid by remember { mutableStateOf(false) }
    val amountFocus = remember { FocusRequester() }
    val reasonFocus = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun focus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (e: IllegalStateException) {
            // champ pas encore affiché
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        focus(amountFocus)
    }

    fun reload() {
        reloadKey += 1
        expanded = emptySet()
    }

    fun submit() {
        val type = if (isIn) CashType.In else CashType.Out
        // Le solde est relu au moment d'enregistrer.
        val balance = app.cash.getCashSummary().balance
        when (val result = validateCashForm(type, amount, reason, balance)) {
            is CashFormResult.Invalid -> {
                if (result.field == MoneyField.Amount) {
                    amountInvalid = true
                    focus(amountFocus)
                } else {
                    reasonInvalid = true
                    focus(reasonFocus)
                }
                toast.show(result.message)
            }
            is CashFormResult.Valid -> {
                val entry = CashEntry(newId(), result.type, result.amount, result.reason, toIso(Instant.now()))
                if (!app.cash.addCashEntry(entry)) {
                    toast.show("Impossible d'enregistrer le mouvement.")
                    return
                }
                amount = ""
                reason = ""
                amountInvalid = false
                reasonInvalid = false
                toast.show(cashSavedMessage(result.type, result.goesNegative))
                reload()
                focus(amountFocus)
            }
        }
    }

    val today = LocalDate.now()
    val rows = remember(summary, today) {
        val result = ArrayList<CashRow>()
        var index = 0
        for (group in groupByDay(summary.movements, today, zone) { it.createdAt }) {
            result.add(CashRow.Day(group.label, "day:" + index))
            index += 1
            for (movement in group.items) {
                result.add(CashRow.Item(movement, "movement:" + index + ":" + movement.id))
                index += 1
            }
        }
        result
    }

    val methods = remember(summary) { summary.salesByMethod.entries.filter { it.value > 0.0 } }

    ModuleScaffold("FINANCES", "Caisse", { nav.goBack() }, toast) {
        item {
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "Solde de la caisse", formatNumber(summary.balance), Modifier.fillMaxWidth(),
                    unit = "FCFA", wide = true, negative = summary.balance < 0.0
                )
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard(
                        "Entrées aujourd'hui", formatNumber(summary.todayIn), Modifier.weight(1f).fillMaxHeight(),
                        unit = "FCFA", valueColor = c.success
                    )
                    SummaryCard(
                        "Sorties aujourd'hui", formatNumber(summary.todayOut), Modifier.weight(1f).fillMaxHeight(),
                        unit = "FCFA", valueColor = c.error
                    )
                }
            }
        }

        if (methods.isNotEmpty()) {
            item {
                ModuleSection("Encaissé sur les ventes") {
                    for ((method, total) in methods) {
                        ModuleDetailRow(cashMethodLabel(method), formatMoney(total))
                    }
                }
            }
        }

        item {
            ModuleSection("Nouveau mouvement") {
                // Dans l'original, les champs suivent directement les deux boutons (sans espace).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TypeButton("Entrée", active = isIn, income = true, Modifier.weight(1f)) { isIn = true }
                    TypeButton("Sortie", active = !isIn, income = false, Modifier.weight(1f)) { isIn = false }
                }
                ModuleField("Montant (FCFA)") {
                    ModuleTextField(
                        amount, { amount = it.filter { ch -> ch.isDigit() }; amountInvalid = false }, "Ex. : 10000",
                        keyboardType = KeyboardType.Number, invalid = amountInvalid, focusRequester = amountFocus
                    )
                }
                Spacer(Modifier.height(12.dp))
                ModuleField("Motif (facultatif)") {
                    ModuleTextField(
                        reason, { reason = it; reasonInvalid = false }, "Ex. : Fonds de caisse du matin",
                        invalid = reasonInvalid, maxLength = 80, focusRequester = reasonFocus
                    )
                }
                Row(Modifier.padding(top = 16.dp)) {
                    PrimaryButton("Enregistrer", Modifier.weight(1f)) { submit() }
                }
            }
        }

        if (loaded && summary.movements.isEmpty()) {
            item {
                ModuleEmpty(
                    "Aucun mouvement",
                    "Les ventes, entrées, sorties et dépenses apparaîtront ici."
                )
            }
        }

        items(rows, key = { it.key }) { row ->
            when (row) {
                is CashRow.Day -> DayHeading(row.label)
                is CashRow.Item -> {
                    val movement = row.movement
                    val income = movement.amount > 0.0
                    val deletableId = movement.deletableId
                    val amountText = (if (income) "+ " else "− ") + formatMoney(kotlin.math.abs(movement.amount))
                    val color = if (income) c.success else c.error

                    if (deletableId != null) {
                        RowCard(
                            expanded = movement.id in expanded,
                            onToggle = {
                                expanded = if (movement.id in expanded) expanded - movement.id else expanded + movement.id
                            },
                            summary = {
                                RowMain(movement.label, cashMovementSubtitle(movement, zone), Modifier.weight(1f))
                                Text(amountText, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            },
                            details = {
                                Column(Modifier.padding(top = 14.dp)) {
                                    DeleteControl(
                                        label = "Supprimer ce mouvement",
                                        question = "Supprimer ce mouvement ? Le solde de la caisse sera mis à jour."
                                    ) {
                                        toast.show(
                                            if (app.cash.deleteCashEntry(deletableId)) "Mouvement supprimé."
                                            else "Impossible de supprimer ce mouvement."
                                        )
                                        reload()
                                    }
                                }
                            }
                        )
                    } else {
                        StaticRowCard {
                            RowMain(movement.label, cashMovementSubtitle(movement, zone), Modifier.weight(1f))
                            Text(amountText, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeButton(label: String, active: Boolean, income: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    val (background, border, foreground) = when {
        active && income -> Triple(c.successSoft, c.success, c.success)
        active -> Triple(c.errorSoft, c.error, c.error)
        else -> Triple(c.surface, c.border, c.textSecondary)
    }

    Box(
        modifier
            .height(44.dp)
            .background(background, shape)
            .border(1.dp, border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
