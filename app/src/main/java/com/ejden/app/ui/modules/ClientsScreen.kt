package com.ejden.app.ui.modules

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.Client
import com.ejden.app.data.ClientField
import com.ejden.app.data.ClientFormResult
import com.ejden.app.data.CreditPayment
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.Sale
import com.ejden.app.data.clientDebtSales
import com.ejden.app.data.clientDebts
import com.ejden.app.data.clientRecentPayments
import com.ejden.app.data.computeRemainingMap
import com.ejden.app.data.describePayment
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.jsTrim
import com.ejden.app.data.newId
import com.ejden.app.data.shortDateText
import com.ejden.app.data.toIso
import com.ejden.app.data.validateClientForm
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private data class ClientsData(val clients: List<Client>, val sales: List<Sale>, val payments: List<CreditPayment>)

@Composable
fun ClientsScreen(nav: NavController, focusNew: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collator = remember { Collator.getInstance(Locale.FRENCH) }
    val zone = remember { ZoneId.systemDefault() }

    var data by remember { mutableStateOf(ClientsData(emptyList(), emptyList(), emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            ClientsData(app.clients.getClients(), app.sales.getSales(), app.credits.getCreditPayments())
        }
        loaded = true
    }

    val remaining = remember(data) { computeRemainingMap(data.sales, data.payments) }
    val debts = remember(data) { clientDebts(data.clients, data.sales, data.payments) }

    // Formulaire.
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var nameInvalid by remember { mutableStateOf(false) }
    var phoneInvalid by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }
    val phoneFocus = remember { FocusRequester() }

    var query by rememberSaveable { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    fun resetForm() {
        editingId = null
        name = ""
        phone = ""
        nameInvalid = false
        phoneInvalid = false
    }

    fun reload() {
        reloadKey += 1
        expanded = emptySet()
    }

    fun focus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (e: IllegalStateException) {
            // champ pas encore affiché
        }
    }

    LaunchedEffect(Unit) {
        if (focusNew) {
            delay(300)
            focus(nameFocus)
        }
    }

    fun submit() {
        when (val result = validateClientForm(name, phone, data.clients, editingId)) {
            is ClientFormResult.Invalid -> {
                if (result.field == ClientField.Name) {
                    nameInvalid = true
                    focus(nameFocus)
                } else {
                    phoneInvalid = true
                    focus(phoneFocus)
                }
                toast.show(result.message)
            }
            is ClientFormResult.Valid -> {
                val id = editingId
                if (id == null) {
                    val client = Client(newId(), result.name, result.phone, toIso(Instant.now()))
                    if (!app.clients.addClient(client)) {
                        toast.show("Impossible d'enregistrer le client.")
                        return
                    }
                    toast.show("Client ajouté.")
                } else {
                    val existing = app.clients.getClients().firstOrNull { it.id == id }
                    if (existing == null) {
                        toast.show("Client introuvable.")
                        resetForm()
                        reload()
                        return
                    }
                    val renamed = existing.name.lowercase() != result.name.lowercase()
                    val hadDebt = (debts[existing.id] ?: 0.0) > 0.0
                    if (!app.clients.updateClient(existing.copy(name = result.name, phone = result.phone))) {
                        toast.show("Impossible d'enregistrer les modifications.")
                        return
                    }
                    toast.show(
                        if (renamed && hadDebt) "Modifié. Les anciens crédits restent liés à l'ancien nom."
                        else "Client modifié."
                    )
                }
                resetForm()
                reload()
            }
        }
    }

    fun startEdit(client: Client) {
        editingId = client.id
        name = client.name
        phone = client.phone
        nameInvalid = false
        phoneInvalid = false
        scope.launch {
            listState.animateScrollToItem(2)
            focus(nameFocus)
        }
    }

    // Liste filtrée.
    val search = query.jsTrim().lowercase()
    val filtered = remember(data.clients, search) {
        data.clients
            .filter {
                search.isEmpty() ||
                    it.name.lowercase().contains(search) ||
                    it.phone.replace(JS_WHITESPACE_RUN, "").contains(search.replace(JS_WHITESPACE_RUN, ""))
            }
            .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
    }
    val totalDebt = debts.values.sum()

    ModuleScaffold("COMMERCE", "Clients", { nav.goBack() }, toast, listState) {
        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Clients", data.clients.size.toString(), Modifier.weight(1f).fillMaxHeight())
                SummaryCard("Total dû", formatNumber(totalDebt), Modifier.weight(1f).fillMaxHeight(), unit = "FCFA")
            }
        }

        item {
            ModuleSection(if (editingId == null) "Nouveau client" else "Modifier le client") {
                ModuleField("Nom du client") {
                    ModuleTextField(
                        name, { name = it; nameInvalid = false }, "Ex. : Awa Koné",
                        invalid = nameInvalid, maxLength = 80, focusRequester = nameFocus
                    )
                }
                Spacer(Modifier.height(12.dp))
                ModuleField("Téléphone (facultatif)") {
                    ModuleTextField(
                        phone, { phone = it; phoneInvalid = false }, "Ex. : +225 07 00 00 00 00",
                        keyboardType = KeyboardType.Phone, invalid = phoneInvalid, maxLength = 20,
                        focusRequester = phoneFocus
                    )
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        if (editingId == null) "Ajouter le client" else "Enregistrer",
                        Modifier.weight(1f)
                    ) { submit() }
                    if (editingId != null) SecondaryButton("Annuler") { resetForm() }
                }
            }
        }

        item { ModuleSearch(query, { query = it }, "Rechercher un client...") }

        if (loaded && filtered.isEmpty()) {
            item {
                ModuleEmpty(
                    if (data.clients.isEmpty()) "Aucun client" else "Aucun résultat",
                    if (data.clients.isEmpty()) "Les clients que vous ajoutez apparaîtront ici."
                    else "Aucun client ne correspond à votre recherche."
                )
            }
        }

        items(filtered, key = { it.id }) { client ->
            ClientCard(
                client = client,
                debt = debts[client.id] ?: 0.0,
                data = data,
                remaining = remaining,
                zone = zone,
                expanded = client.id in expanded,
                onToggle = {
                    expanded = if (client.id in expanded) expanded - client.id else expanded + client.id
                },
                onPhone = {
                    val digits = client.phone.replace(Regex("[^0-9+]"), "")
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                    } catch (e: Exception) {
                        toast.show("Impossible d'ouvrir le téléphone.")
                    }
                },
                onPayWith = { saleIds, amount, method, due ->
                    val outcome = describePayment(
                        app.credits.recordCreditPayment(saleIds, amount.toDouble(), method),
                        amount.toDouble(), due
                    )
                    toast.show(outcome.message)
                    if (outcome.success) reload()
                    outcome.success
                },
                onError = { toast.show(it) },
                onEdit = { startEdit(client) },
                onDelete = {
                    if (!app.clients.deleteClient(client.id)) {
                        toast.show("Impossible de supprimer ce client.")
                    } else {
                        if (editingId == client.id) resetForm()
                        toast.show("Client supprimé.")
                    }
                    reload()
                }
            )
        }
    }
}

@Composable
private fun ClientCard(
    client: Client,
    debt: Double,
    data: ClientsData,
    remaining: Map<String, Double>,
    zone: ZoneId,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPhone: () -> Unit,
    onPayWith: (List<String>, Int, com.ejden.app.data.PaymentMethod, Double) -> Boolean,
    onError: (String) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Ejden.colors

    RowCard(
        expanded = expanded,
        onToggle = onToggle,
        summary = {
            RowMain(
                client.name,
                client.phone.ifEmpty { "Pas de téléphone" },
                Modifier.weight(1f)
            )
            if (debt > 0.0) Badge("Doit " + formatMoney(debt), BadgeTone.Debt) else Badge("À jour", BadgeTone.Success)
        },
        details = {
            if (client.phone.isNotEmpty()) {
                ModuleDetailRow("Téléphone", client.phone, valueColor = c.primary, onValueClick = onPhone)
            }
            ModuleDetailRow("Client depuis", shortDateText(client.createdAt, zone))

            val debtSales = remember(data, client) { clientDebtSales(client, data.sales, remaining) }
            if (debtSales.isNotEmpty()) {
                ModuleDetailTitle("Crédits en cours")
                for (sale in debtSales) {
                    ModuleDetailRow(
                        shortDateText(sale.createdAt, zone) + " · total " + formatMoney(sale.total),
                        "Reste " + formatMoney(remaining[sale.id] ?: 0.0)
                    )
                }
                // Le paiement est réparti sur les crédits, du plus ancien au plus récent.
                val saleIds = debtSales.reversed().map { it.id }
                PaymentForm(
                    due = debt,
                    onPay = { amount, method -> onPayWith(saleIds, amount, method, debt) },
                    onError = onError
                )
            }

            val payments = remember(data, client) { clientRecentPayments(client, data.sales, data.payments, 5) }
            if (payments.isNotEmpty()) {
                ModuleDetailTitle("Derniers paiements reçus")
                PaymentsList(payments, zone)
            }

            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleActionButton("Modifier", ModuleButtonStyle.Normal, onClick = onEdit)
                DeleteControl(
                    label = "Supprimer ce client",
                    question = if (debt > 0.0) {
                        "${client.name} doit encore ${formatMoney(debt)}. Supprimer sa fiche ? Les ventes restent dans l'historique."
                    } else {
                        "Supprimer cette fiche client ? Les ventes restent dans l'historique."
                    },
                    onConfirm = onDelete
                )
            }
        }
    )
}
