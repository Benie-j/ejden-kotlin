package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.AddOrderResult
import com.ejden.app.data.Client
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.Limits
import com.ejden.app.data.ModuleLimits
import com.ejden.app.data.Order
import com.ejden.app.data.OrderInput
import com.ejden.app.data.OrderInputLine
import com.ejden.app.data.OrderKind
import com.ejden.app.data.OrderRejection
import com.ejden.app.data.OrdersSummary
import com.ejden.app.data.Product
import com.ejden.app.data.Supplier
import com.ejden.app.data.cleanAmount
import com.ejden.app.data.formatMoney
import com.ejden.app.data.jsTrim
import com.ejden.app.data.plainNumber
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ORDER_LINES_MAX = 50

private data class OrdersData(
    val suppliers: List<Supplier>,
    val clients: List<Client>,
    val products: List<Product>,
    val orders: List<Order>,
    val summary: OrdersSummary
)

/** Une ligne du formulaire (produit, quantité, prix) avant enregistrement. */
private class OrderLineDraft(val id: Int) {
    var productId by mutableStateOf("")
    var quantity by mutableStateOf("")
    var unitPrice by mutableStateOf("")
    var productInvalid by mutableStateOf(false)
    var quantityInvalid by mutableStateOf(false)
    var priceInvalid by mutableStateOf(false)
}

private fun orderLineAmount(line: OrderLineDraft): Long {
    val quantity = line.quantity.toLongOrNull() ?: return 0L
    val price = line.unitPrice.toLongOrNull() ?: return 0L
    return if (quantity > 0L && price > 0L) quantity * price else 0L
}

private fun orderRejectionMessage(reason: OrderRejection): String = when (reason) {
    OrderRejection.PartyNotFound -> "Client ou fournisseur introuvable."
    OrderRejection.PartyRequired -> "Choisissez un fournisseur."
    OrderRejection.NoItems -> "Ajoutez au moins un produit."
    OrderRejection.InvalidItem -> "Quantité ou prix invalide sur une ligne."
    OrderRejection.UnknownProduct -> "Un produit n'existe plus. Rechargez la page."
    OrderRejection.DuplicateProduct -> "Un même produit apparaît deux fois : regroupez les lignes."
    OrderRejection.TooHigh -> "Quantité ou montant trop grand."
    OrderRejection.InvalidDate -> "Date prévue invalide."
    OrderRejection.Storage -> "Impossible d'enregistrer la commande."
}

/** « 2026-10-20 » → « 20/10/2026 ». */
internal fun orderDayText(day: String): String =
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

@Composable
fun OrdersScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()
    val collator = remember { Collator.getInstance(Locale.FRENCH) }

    var data by remember {
        mutableStateOf(OrdersData(emptyList(), emptyList(), emptyList(), emptyList(), OrdersSummary(0, 0, 0)))
    }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            OrdersData(
                app.suppliers.getSuppliers(),
                app.clients.getClients(),
                app.products.getProducts(),
                app.orders.getOrders(),
                app.orders.getOrdersSummary()
            )
        }
        loaded = true
    }

    // Type de commande, filtre et carte ouverte (la liste arrive à l'étape suivante).
    var kindId by rememberSaveable { mutableStateOf("customer") }
    val customer = kindId == "customer"
    val kind = if (customer) OrderKind.Customer else OrderKind.Supplier
    var filterKey by rememberSaveable { mutableStateOf("open") }
    var openOrderId by remember { mutableStateOf<String?>(null) }

    // Formulaire.
    var partyId by rememberSaveable { mutableStateOf("") }
    var partyInvalid by remember { mutableStateOf(false) }
    var nextLineId by remember { mutableIntStateOf(1) }
    val lines = remember { mutableStateListOf(OrderLineDraft(0)) }
    var day by rememberSaveable { mutableStateOf("") }
    var showDayPicker by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val partyOptions = remember(customer, data.clients, data.suppliers) {
        if (customer) {
            listOf("" to "Client de passage") +
                data.clients
                    .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                    .map { it.id to it.name }
        } else {
            listOf("" to "Choisir un fournisseur…") +
                data.suppliers
                    .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                    .map { it.id to it.name }
        }
    }
    val productOptions = remember(data.products) {
        listOf("" to "Choisir un produit…") +
            data.products
                .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                .map { it.id to it.name }
    }

    // Un client ou fournisseur supprimé entre-temps n'est plus sélectionné.
    val selectedParty = if (partyOptions.any { it.first == partyId }) partyId else ""
    val total = lines.sumOf { orderLineAmount(it) }

    val blocked = when {
        !loaded -> ""
        data.products.isEmpty() -> "Ajoutez d'abord des produits pour pouvoir les commander."
        !customer && data.suppliers.isEmpty() -> "Ajoutez d'abord un fournisseur dans la page Fournisseurs."
        else -> ""
    }

    fun resetLines() {
        lines.clear()
        lines.add(OrderLineDraft(nextLineId))
        nextLineId += 1
    }

    fun switchKind(newKindId: String) {
        if (newKindId == kindId) return
        kindId = newKindId
        openOrderId = null
        partyId = ""
        partyInvalid = false
        resetLines()
    }

    fun addLine() {
        if (lines.size >= ORDER_LINES_MAX) {
            toast.show("Maximum 50 produits par commande.")
            return
        }
        lines.add(OrderLineDraft(nextLineId))
        nextLineId += 1
    }

    fun removeLine(line: OrderLineDraft) {
        if (lines.size <= 1) {
            toast.show("Une commande doit contenir au moins un produit.")
            return
        }
        lines.remove(line)
    }

    fun chooseProduct(line: OrderLineDraft, id: String) {
        line.productId = id
        line.productInvalid = false
        // Choisir un produit propose son prix (vente ou achat selon le type).
        if (line.unitPrice.isBlank()) {
            val chosen = data.products.firstOrNull { it.id == id }
            if (chosen != null) {
                line.unitPrice = plainNumber(if (customer) chosen.salePrice else chosen.purchasePrice)
            }
        }
    }

    fun submit() {
        if (!customer && selectedParty.isEmpty()) {
            partyInvalid = true
            toast.show("Choisissez un fournisseur.")
            return
        }

        val items = ArrayList<OrderInputLine>()
        for (line in lines) {
            if (line.productId.isEmpty()) {
                line.productInvalid = true
                toast.show("Choisissez un produit sur chaque ligne.")
                return
            }
            val quantity = line.quantity.toLongOrNull()
            if (quantity == null || quantity < 1L || quantity.toDouble() > Limits.STOCK_MAX) {
                line.quantityInvalid = true
                toast.show("Quantité invalide : un nombre entier est attendu.")
                return
            }
            val price = line.unitPrice.toLongOrNull()
            if (price == null || cleanAmount(price.toDouble()) == null) {
                line.priceInvalid = true
                toast.show("Prix invalide.")
                return
            }
            items.add(OrderInputLine(line.productId, quantity.toInt(), price.toDouble()))
        }

        val cleanNote = note.replace(JS_WHITESPACE_RUN, " ").jsTrim()
        if (cleanNote.length > ModuleLimits.TEXT_MAX) {
            toast.show("Note trop longue (${ModuleLimits.TEXT_MAX} caractères max).")
            return
        }

        val input = OrderInput(
            kind = kind,
            partyId = selectedParty.ifEmpty { null },
            lines = items,
            note = cleanNote,
            expectedDate = day.ifEmpty { null }
        )

        when (val result = app.orders.addOrder(input)) {
            is AddOrderResult.Rejected -> toast.show(orderRejectionMessage(result.reason))
            is AddOrderResult.Ok -> {
                toast.show("Commande enregistrée.")
                partyId = ""
                day = ""
                note = ""
                resetLines()
                filterKey = "open"
                openOrderId = result.order.id
                reloadKey += 1
            }
        }
    }

    ModuleScaffold("GESTION", "Commandes", { nav.goBack() }, toast, listState) {
        item {
            Column(Modifier.padding(bottom = 14.dp)) {
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        "Commandes clients en cours",
                        data.summary.customerOpen.toString(),
                        Modifier.weight(1f).fillMaxHeight()
                    )
                    SummaryCard(
                        "Commandes fournisseurs en cours",
                        data.summary.supplierOpen.toString(),
                        Modifier.weight(1f).fillMaxHeight()
                    )
                }
                if (data.summary.overdue > 0) {
                    Spacer(Modifier.height(8.dp))
                    OrderAlertCard("En retard", data.summary.overdue.toString())
                }
            }
        }

        item {
            OrderKindToggle(customer) { switchKind(it) }
        }

        item {
            ModuleSection(if (customer) "Nouvelle commande client" else "Nouvelle commande fournisseur") {
                if (blocked.isNotEmpty()) {
                    Text(blocked, color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                } else {
                    ModuleField(if (customer) "Client" else "Fournisseur") {
                        OrderFieldFrame(partyInvalid) {
                            ModuleSelect(selectedParty, partyOptions) {
                                partyId = it
                                partyInvalid = false
                            }
                        }
                    }

                    ModuleDetailTitle("Produits commandés")

                    for (line in lines) {
                        key(line.id) {
                            OrderLineCard(
                                line = line,
                                customer = customer,
                                productOptions = productOptions,
                                onChooseProduct = { chooseProduct(line, it) },
                                onRemove = { removeLine(line) }
                            )
                        }
                    }

                    ModuleActionButton(
                        "＋ Ajouter un produit", ModuleButtonStyle.Normal, Modifier.padding(top = 10.dp)
                    ) { addLine() }

                    Row(
                        Modifier
                            .padding(top = 16.dp, bottom = 12.dp)
                            .fillMaxWidth()
                            .background(c.primarySoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total de la commande", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(formatMoney(total.toDouble()), color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }

                    ModuleField("Date prévue (facultatif)") {
                        OrderDateField(day, onOpen = { showDayPicker = true }, onClear = { day = "" })
                    }

                    Spacer(Modifier.height(12.dp))
                    ModuleField("Note (facultatif)") {
                        ModuleTextField(
                            note, { note = it }, "Ex. : à livrer le matin",
                            maxLength = 80, imeAction = ImeAction.Done
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Enregistrer la commande", Modifier.fillMaxWidth()) { submit() }
                }
            }
        }

        orderList(data.orders, data.products, kind, filterKey, { filterKey = it }, openOrderId, loaded, toast) {
            reloadKey += 1
        }
    }

    if (showDayPicker) {
        OrderDayPickerDialog(
            initialDay = day,
            onPick = { day = it },
            onDismiss = { showDayPicker = false }
        )
    }
}

/** Bascule « Clients » / « Fournisseurs » (.segmented orders-kind). */
@Composable
private fun OrderKindToggle(customer: Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier.padding(bottom = 14.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        KindButton("Clients", customer, Modifier.weight(1f)) { onSelect("customer") }
        KindButton("Fournisseurs", !customer, Modifier.weight(1f)) { onSelect("supplier") }
    }
}

@Composable
private fun KindButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(44.dp)
            .background(if (active) c.primarySoft else c.surface, shape)
            .border(1.dp, if (active) c.primary else c.border, shape)
            .tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) c.primary else c.textSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Carte « En retard » (.summary-card.is-alert). */
@Composable
private fun OrderAlertCard(label: String, value: String) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.errorSoft, shape)
            .border(1.dp, c.error, shape)
            .padding(14.dp)
    ) {
        Text(label, color = c.error, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.error, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Entoure un champ de liste d'un liseré rouge quand il est en erreur. */
@Composable
private fun OrderFieldFrame(invalid: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(if (invalid) Modifier.border(1.dp, Ejden.colors.error, shape) else Modifier) { content() }
}

@Composable
private fun OrderLineCard(
    line: OrderLineDraft,
    customer: Boolean,
    productOptions: List<Pair<String, String>>,
    onChooseProduct: (String) -> Unit,
    onRemove: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)

    Column(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField("Produit") {
            OrderFieldFrame(line.productInvalid) {
                ModuleSelect(line.productId, productOptions) { onChooseProduct(it) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ModuleField("Quantité") {
                    ModuleTextField(
                        line.quantity,
                        {
                            line.quantity = it.filter { ch -> ch.isDigit() }
                            line.quantityInvalid = false
                        },
                        "Ex. : 6",
                        keyboardType = KeyboardType.Number,
                        invalid = line.quantityInvalid,
                        maxLength = 7
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ModuleField(if (customer) "Prix de vente" else "Prix d'achat") {
                    ModuleTextField(
                        line.unitPrice,
                        {
                            line.unitPrice = it.filter { ch -> ch.isDigit() }
                            line.priceInvalid = false
                        },
                        "FCFA",
                        keyboardType = KeyboardType.Number,
                        invalid = line.priceInvalid,
                        maxLength = 9
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total de la ligne : ", color = c.textSecondary, fontSize = 12.sp)
            Text(
                formatMoney(orderLineAmount(line).toDouble()),
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Retirer",
                modifier = Modifier
                    .background(c.surface, CircleShape)
                    .border(1.dp, c.error, CircleShape)
                    .tap(onRemove)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = c.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Champ « Date prévue » : touche pour choisir, « Effacer » pour retirer. */
@Composable
private fun OrderDateField(day: String, onOpen: () -> Unit, onClear: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap(onOpen)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            if (day.isEmpty()) "jj/mm/aaaa" else orderDayText(day),
            modifier = Modifier.weight(1f),
            color = if (day.isEmpty()) c.textSecondary else c.text,
            fontSize = 14.sp
        )
        if (day.isNotEmpty()) {
            Text(
                "Effacer",
                modifier = Modifier.tap(onClear).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                color = c.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Sélecteur de jour : les dates passées sont interdites (comme le min du champ HTML). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrderDayPickerDialog(initialDay: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val initialMillis = try {
        LocalDate.parse(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(today)
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    onDismiss()
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    ) {
        DatePicker(state = state)
    }
}
