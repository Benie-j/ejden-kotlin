package com.ejden.app.ui.modules

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.CREDIT_PAYMENT_METHODS
import com.ejden.app.data.CancelSupplierPaymentResult
import com.ejden.app.data.DeleteSupplierResult
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.Purchase
import com.ejden.app.data.Supplier
import com.ejden.app.data.SupplierDebt
import com.ejden.app.data.SupplierField
import com.ejden.app.data.SupplierFormResult
import com.ejden.app.data.SupplierPayment
import com.ejden.app.data.SupplierPaymentBatch
import com.ejden.app.data.SupplierPaymentResult
import com.ejden.app.data.computePurchaseRemainingMap
import com.ejden.app.data.computeSupplierDebt
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.groupSupplierPaymentBatches
import com.ejden.app.data.jsTrim
import com.ejden.app.data.newId
import com.ejden.app.data.paymentLabel
import com.ejden.app.data.plainNumber
import com.ejden.app.data.shortDateText
import com.ejden.app.data.timeText
import com.ejden.app.data.toIso
import com.ejden.app.data.validateSupplierForm
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private data class SuppliersData(
    val suppliers: List<Supplier>,
    val purchases: List<Purchase>,
    val payments: List<SupplierPayment>
)

@Composable
fun SuppliersScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val collator = remember { Collator.getInstance(Locale.FRENCH) }
    val zone = remember { ZoneId.systemDefault() }

    var data by remember { mutableStateOf(SuppliersData(emptyList(), emptyList(), emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            SuppliersData(
                app.suppliers.getSuppliers(),
                app.purchases.getPurchases(),
                app.supplierPayments.getSupplierPayments()
            )
        }
        loaded = true
    }

    val remaining = remember(data) { computePurchaseRemainingMap(data.purchases, data.payments) }
    val totalDebt = remember(remaining) { remaining.values.sum() }

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
    }

    fun focus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (e: IllegalStateException) {
            // champ pas encore affiché
        }
    }

    fun submit() {
        when (val result = validateSupplierForm(name, phone, data.suppliers, editingId)) {
            is SupplierFormResult.Invalid -> {
                if (result.field == SupplierField.Name) {
                    nameInvalid = true
                    focus(nameFocus)
                } else {
                    phoneInvalid = true
                    focus(phoneFocus)
                }
                toast.show(result.message)
            }
            is SupplierFormResult.Valid -> {
                val id = editingId
                if (id == null) {
                    val supplier = Supplier(newId(), result.name, result.phone, toIso(Instant.now()))
                    if (!app.suppliers.addSupplier(supplier)) {
                        toast.show("Impossible d'enregistrer le fournisseur.")
                        return
                    }
                    toast.show("Fournisseur ajouté.")
                } else {
                    val existing = app.suppliers.getSupplierById(id)
                    if (existing == null) {
                        toast.show("Fournisseur introuvable.")
                        resetForm()
                        reload()
                        return
                    }
                    if (!app.suppliers.updateSupplier(existing.copy(name = result.name, phone = result.phone))) {
                        toast.show("Impossible d'enregistrer les modifications.")
                        return
                    }
                    toast.show("Fournisseur modifié. Les anciens achats gardent l'ancien nom.")
                }
                resetForm()
                reload()
            }
        }
    }

    fun startEdit(supplier: Supplier) {
        editingId = supplier.id
        name = supplier.name
        phone = supplier.phone
        nameInvalid = false
        phoneInvalid = false
        scope.launch {
            listState.animateScrollToItem(2)
            focus(nameFocus)
        }
    }

    // Paiement au fournisseur : la caisse peut devenir négative (le paiement reste possible).
    fun pay(supplier: Supplier, amount: Double, method: PaymentMethod): Boolean {
        val goesNegative = app.cash.getCashSummary().balance - amount < 0.0
        return when (val result = app.supplierPayments.recordSupplierPayment(supplier.id, amount, method)) {
            SupplierPaymentResult.Ok -> {
                toast.show(
                    if (goesNegative) "Paiement enregistré. Attention : le solde de la caisse est négatif."
                    else "Paiement de " + formatMoney(amount) + " enregistré."
                )
                reload()
                true
            }
            is SupplierPaymentResult.TooMuch -> {
                toast.show("Montant supérieur à la dette (" + formatMoney(result.debt) + ").")
                false
            }
            SupplierPaymentResult.NoDebt -> {
                toast.show("Aucune dette envers ce fournisseur.")
                false
            }
            SupplierPaymentResult.InvalidAmount -> {
                toast.show("Montant invalide.")
                false
            }
            SupplierPaymentResult.InvalidMethod, SupplierPaymentResult.StorageError -> {
                toast.show("Impossible d'enregistrer le paiement.")
                false
            }
        }
    }

    fun cancelBatch(batchId: String) {
        val ok = app.supplierPayments.cancelSupplierPayment(batchId) == CancelSupplierPaymentResult.Ok
        toast.show(
            if (ok) "Paiement annulé : la dette est remise." else "Impossible d'annuler ce paiement."
        )
        reload()
    }

    fun removeSupplier(supplier: Supplier) {
        when (app.suppliers.deleteSupplier(supplier.id)) {
            DeleteSupplierResult.Ok -> {
                if (editingId == supplier.id) resetForm()
                expanded = expanded - supplier.id
                toast.show("Fournisseur supprimé.")
            }
            DeleteSupplierResult.HasDebt ->
                toast.show("Impossible : vous devez encore de l'argent à ce fournisseur.")
            else -> toast.show("Impossible de supprimer ce fournisseur.")
        }
        reload()
    }

    // Liste filtrée.
    val search = query.jsTrim().lowercase()
    val filtered = remember(data.suppliers, search) {
        data.suppliers
            .filter {
                search.isEmpty() ||
                    it.name.lowercase().contains(search) ||
                    it.phone.replace(JS_WHITESPACE_RUN, "").contains(search.replace(JS_WHITESPACE_RUN, ""))
            }
            .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
    }

    ModuleScaffold(
        "GESTION", "Fournisseurs", { nav.goBack() }, toast, listState,
        headerAction = { HeaderLink("Achats") { nav.open(Routes.PURCHASES) } }
    ) {
        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Fournisseurs", data.suppliers.size.toString(), Modifier.weight(1f).fillMaxHeight())
                SummaryCard("Total à payer", formatNumber(totalDebt), Modifier.weight(1f).fillMaxHeight(), unit = "FCFA")
            }
        }

        item {
            ModuleSection(if (editingId == null) "Nouveau fournisseur" else "Modifier le fournisseur") {
                ModuleField("Nom du fournisseur") {
                    ModuleTextField(
                        name, { name = it; nameInvalid = false }, "Ex. : Grossiste Koffi",
                        invalid = nameInvalid, maxLength = 80, focusRequester = nameFocus
                    )
                }
                Spacer(Modifier.height(12.dp))
                ModuleField("Téléphone (facultatif)") {
                    ModuleTextField(
                        phone, { phone = it; phoneInvalid = false }, "Ex. : +225 05 00 00 00 00",
                        keyboardType = KeyboardType.Phone, invalid = phoneInvalid, maxLength = 20,
                        focusRequester = phoneFocus
                    )
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton(
                        if (editingId == null) "Ajouter le fournisseur" else "Enregistrer",
                        Modifier.weight(1f)
                    ) { submit() }
                    if (editingId != null) SecondaryButton("Annuler") { resetForm() }
                }
            }
        }

        item { ModuleSearch(query, { query = it }, "Rechercher un fournisseur...") }

        if (loaded && filtered.isEmpty()) {
            item {
                ModuleEmpty(
                    if (data.suppliers.isEmpty()) "Aucun fournisseur" else "Aucun résultat",
                    if (data.suppliers.isEmpty()) "Les fournisseurs que vous ajoutez apparaîtront ici."
                    else "Aucun fournisseur ne correspond à votre recherche."
                )
            }
        }

        items(filtered, key = { it.id }) { supplier ->
            val debt = remember(data, supplier) {
                computeSupplierDebt(data.purchases, data.payments, supplier.id)
            }
            val batches = remember(data, supplier) {
                groupSupplierPaymentBatches(data.payments, supplier.id).take(6)
            }
            val purchaseCount = remember(data, supplier) {
                data.purchases.count { it.supplierId == supplier.id && !it.isCancelled }
            }

            SupplierCard(
                supplier = supplier,
                debt = debt,
                purchaseCount = purchaseCount,
                batches = batches,
                zone = zone,
                expanded = supplier.id in expanded,
                resetToken = reloadKey,
                onToggle = {
                    expanded = if (supplier.id in expanded) expanded - supplier.id else expanded + supplier.id
                },
                onPhone = {
                    val digits = supplier.phone.replace(Regex("[^0-9+]"), "")
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
                    } catch (e: Exception) {
                        toast.show("Impossible d'ouvrir le téléphone.")
                    }
                },
                onPay = { amount, method -> pay(supplier, amount, method) },
                onError = { toast.show(it) },
                onCancelBatch = { cancelBatch(it) },
                onBuy = { nav.open(Routes.purchasesFor(supplier.id)) },
                onEdit = { startEdit(supplier) },
                onDelete = { removeSupplier(supplier) }
            )
        }
    }
}

@Composable
private fun SupplierCard(
    supplier: Supplier,
    debt: SupplierDebt,
    purchaseCount: Int,
    batches: List<SupplierPaymentBatch>,
    zone: ZoneId,
    expanded: Boolean,
    resetToken: Int,
    onToggle: () -> Unit,
    onPhone: () -> Unit,
    onPay: (Double, PaymentMethod) -> Boolean,
    onError: (String) -> Unit,
    onCancelBatch: (String) -> Unit,
    onBuy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val c = Ejden.colors

    RowCard(
        expanded = expanded,
        onToggle = onToggle,
        summary = {
            RowMain(
                supplier.name,
                supplier.phone.ifEmpty { "Pas de téléphone" },
                Modifier.weight(1f)
            )
            if (debt.amount > 0.0) {
                Badge("À payer " + formatMoney(debt.amount), BadgeTone.Debt)
            } else {
                Badge("À jour", BadgeTone.Success)
            }
        },
        details = {
            if (supplier.phone.isNotEmpty()) {
                ModuleDetailRow("Téléphone", supplier.phone, valueColor = c.primary, onValueClick = onPhone)
            }
            ModuleDetailRow("Fournisseur depuis", shortDateText(supplier.createdAt, zone))
            ModuleDetailRow("Achats", purchaseCount.toString())

            if (debt.purchases.isNotEmpty()) {
                ModuleDetailTitle("Achats à payer")
                for (entry in debt.purchases) {
                    ModuleDetailRow(
                        shortDateText(entry.purchase.createdAt, zone) + " · total " + formatMoney(entry.purchase.total),
                        "Reste " + formatMoney(entry.remaining)
                    )
                }
                SupplierPayBox(due = debt.amount, onPay = onPay, onError = onError)
            }

            if (batches.isNotEmpty()) {
                ModuleDetailTitle("Paiements faits")
                for (batch in batches) {
                    PaymentBatchLine(batch, zone) { onCancelBatch(batch.batchId) }
                }
            }

            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleActionButton("Nouvel achat", ModuleButtonStyle.Normal, onClick = onBuy)
                ModuleActionButton("Modifier", ModuleButtonStyle.Normal, onClick = onEdit)
                key(resetToken) {
                    DeleteControl(
                        label = "Supprimer ce fournisseur",
                        question = "Supprimer cette fiche ? Les achats déjà faits restent dans l'historique.",
                        onConfirm = onDelete
                    )
                }
            }
        }
    )
}

/** Zone de paiement du fournisseur : montant, « Tout payer », mode, bouton. */
@Composable
private fun SupplierPayBox(
    due: Double,
    onPay: (Double, PaymentMethod) -> Boolean,
    onError: (String) -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(CREDIT_PAYMENT_METHODS.first()) }
    var invalid by remember { mutableStateOf(false) }

    Column(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField("Montant versé (FCFA)") {
            ModuleTextField(
                amountText,
                { amountText = it.filter { ch -> ch.isDigit() }; invalid = false },
                "Max " + formatNumber(due),
                keyboardType = KeyboardType.Number,
                invalid = invalid,
                maxLength = 12,
                imeAction = ImeAction.Done
            )
        }
        Text(
            "Tout payer (" + formatMoney(due) + ")",
            modifier = Modifier.padding(top = 8.dp).tap { amountText = plainNumber(due); invalid = false },
            color = c.primary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        ModuleField("Mode de paiement") {
            ModuleSelect(method.id, CREDIT_PAYMENT_METHODS.map { it.id to paymentLabel(it) }) { id ->
                val chosen = PaymentMethod.fromId(id)
                if (chosen != null) method = chosen
            }
        }
        Spacer(Modifier.height(12.dp))
        ModuleActionButton("Payer le fournisseur", ModuleButtonStyle.Primary) {
            val value = amountText.toLongOrNull()
            if (value == null || value <= 0L) {
                invalid = true
                onError("Montant invalide.")
            } else if (onPay(value.toDouble(), method)) {
                amountText = ""
            } else {
                invalid = true
            }
        }
    }
}

/** Un versement (peut regrouper plusieurs achats) ; annulation en deux temps. */
@Composable
private fun PaymentBatchLine(batch: SupplierPaymentBatch, zone: ZoneId, onCancel: () -> Unit) {
    val c = Ejden.colors
    val cancelled = batch.cancelledAt != null
    val strike = if (cancelled) TextDecoration.LineThrough else null
    var confirming by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).alpha(if (cancelled) 0.5f else 1f)) {
            Text(formatMoney(batch.amount), color = c.text, fontSize = 13.sp, textDecoration = strike)
            Text(
                paymentLabel(batch.method) + " · " + shortDateText(batch.createdAt, zone) + " " +
                    timeText(batch.createdAt, zone) + (if (cancelled) " · annulé" else ""),
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 11.sp,
                textDecoration = strike
            )
        }
        if (!cancelled) {
            Text(
                if (confirming) "Confirmer ?" else "Annuler",
                modifier = Modifier
                    .padding(start = 12.dp)
                    .background(c.surface, CircleShape)
                    .border(1.dp, c.border, CircleShape)
                    .tap { if (confirming) onCancel() else confirming = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = c.error,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
