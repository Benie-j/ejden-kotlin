package com.ejden.app.ui.modules

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Expense
import com.ejden.app.data.ExpenseCategory
import com.ejden.app.data.ExpenseFormResult
import com.ejden.app.data.ExpenseTotals
import com.ejden.app.data.MoneyField
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.groupByDay
import com.ejden.app.data.newId
import com.ejden.app.data.parseDateMillis
import com.ejden.app.data.timeText
import com.ejden.app.data.toIso
import com.ejden.app.data.validateExpenseForm
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed class ExpenseRow(val key: String) {
    class Day(val label: String, key: String) : ExpenseRow(key)
    class Item(val expense: Expense, key: String) : ExpenseRow(key)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpensesScreen(nav: NavController, focusNew: Boolean) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val zone = remember { ZoneId.systemDefault() }

    var expenses by remember { mutableStateOf<List<Expense>>(emptyList()) }
    var totals by remember { mutableStateOf(ExpenseTotals(0.0, 0.0)) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        val data = withContext(Dispatchers.Default) { app.expenses.getExpenses() to app.expenses.getExpensesTotals() }
        expenses = data.first
        totals = data.second
        loaded = true
    }

    val categoryOptions = remember { ExpenseCategory.entries.map { it.id to it.label } }

    var label by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(ExpenseCategory.entries.first().id) }
    var labelInvalid by remember { mutableStateOf(false) }
    var amountInvalid by remember { mutableStateOf(false) }
    val labelFocus = remember { FocusRequester() }
    val amountFocus = remember { FocusRequester() }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun focus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (e: IllegalStateException) {
            // champ pas encore affiché
        }
    }

    // Le montant est toujours prêt à être saisi.
    LaunchedEffect(Unit) {
        delay(300)
        focus(amountFocus)
    }

    fun reload() {
        reloadKey += 1
        expanded = emptySet()
    }

    fun submit() {
        val category = ExpenseCategory.fromId(categoryId) ?: ExpenseCategory.Other
        when (val result = validateExpenseForm(label, amount, category)) {
            is ExpenseFormResult.Invalid -> {
                if (result.field == MoneyField.Text) {
                    labelInvalid = true
                    focus(labelFocus)
                } else {
                    amountInvalid = true
                    focus(amountFocus)
                }
                toast.show(result.message)
            }
            is ExpenseFormResult.Valid -> {
                val expense = Expense(newId(), result.label, result.amount, result.category, toIso(Instant.now()))
                if (!app.expenses.addExpense(expense)) {
                    toast.show("Impossible d'enregistrer la dépense.")
                    return
                }
                label = ""
                amount = ""
                categoryId = ExpenseCategory.entries.first().id
                labelInvalid = false
                amountInvalid = false
                toast.show("Dépense enregistrée.")
                reload()
                focus(amountFocus)
            }
        }
    }

    val today = LocalDate.now()
    val rows = remember(expenses, today) {
        val sorted = expenses.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
        val result = ArrayList<ExpenseRow>()
        var index = 0
        for (group in groupByDay(sorted, today, zone) { it.createdAt }) {
            result.add(ExpenseRow.Day(group.label, "day:" + index))
            index += 1
            for (expense in group.items) {
                result.add(ExpenseRow.Item(expense, "expense:" + index + ":" + expense.id))
                index += 1
            }
        }
        result
    }

    ModuleScaffold("FINANCES", "Dépenses", { nav.goBack() }, toast) {
        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Aujourd'hui", formatNumber(totals.today), Modifier.weight(1f).fillMaxHeight(), unit = "FCFA")
                SummaryCard("Ce mois-ci", formatNumber(totals.month), Modifier.weight(1f).fillMaxHeight(), unit = "FCFA")
            }
        }

        item {
            ModuleSection("Nouvelle dépense") {
                ModuleField("Montant (FCFA)") {
                    ModuleTextField(
                        amount, { amount = it.filter { ch -> ch.isDigit() }; amountInvalid = false }, "Ex. : 5000",
                        keyboardType = KeyboardType.Number, invalid = amountInvalid, focusRequester = amountFocus
                    )
                }
                Spacer(Modifier.height(12.dp))
                ModuleField("Catégorie") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for ((id, name) in categoryOptions) {
                            FilterPill(name, id == categoryId) { categoryId = id }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ModuleField("Description (facultatif)") {
                    ModuleTextField(
                        label, { label = it; labelInvalid = false }, "Ex. : Transport de marchandises",
                        invalid = labelInvalid, maxLength = 80, focusRequester = labelFocus
                    )
                }
                Row(Modifier.padding(top = 16.dp)) {
                    PrimaryButton("Enregistrer la dépense", Modifier.weight(1f)) { submit() }
                }
            }
        }

        if (loaded && expenses.isEmpty()) {
            item { ModuleEmpty("Aucune dépense", "Les dépenses enregistrées apparaîtront ici.") }
        }

        items(rows, key = { it.key }) { row ->
            when (row) {
                is ExpenseRow.Day -> DayHeading(row.label)
                is ExpenseRow.Item -> {
                    val expense = row.expense
                    RowCard(
                        expanded = expense.id in expanded,
                        onToggle = {
                            expanded = if (expense.id in expanded) expanded - expense.id else expanded + expense.id
                        },
                        summary = {
                            RowMain(
                                expense.label,
                                expense.category.label + " · " + timeText(expense.createdAt, zone),
                                Modifier.weight(1f)
                            )
                            Text(
                                "− " + formatMoney(expense.amount),
                                color = c.error,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        },
                        details = {
                            Column(Modifier.padding(top = 14.dp)) {
                                DeleteControl(
                                    label = "Supprimer cette dépense",
                                    question = "Supprimer cette dépense ? Le solde de la caisse sera mis à jour."
                                ) {
                                    toast.show(
                                        if (app.expenses.deleteExpense(expense.id)) "Dépense supprimée."
                                        else "Impossible de supprimer cette dépense."
                                    )
                                    reload()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
