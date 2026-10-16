package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.Limits
import com.ejden.app.data.ModuleLimits
import com.ejden.app.data.Product
import com.ejden.app.data.PurchaseInput
import com.ejden.app.data.PurchaseLine
import com.ejden.app.data.PurchaseRejection
import com.ejden.app.data.RecordPurchaseResult
import com.ejden.app.data.Supplier
import com.ejden.app.data.cleanAmount
import com.ejden.app.data.formatMoney
import com.ejden.app.data.jsTrim
import com.ejden.app.data.plainNumber
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.backTo
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

private const val MAX_LINES = 50

private data class PurchaseFormData(val suppliers: List<Supplier>, val products: List<Product>)

/** Une ligne du formulaire (produit, quantité, prix) avant enregistrement. */
private class LineDraft(val id: Int) {
    var productId by mutableStateOf("")
    var quantity by mutableStateOf("")
    var unitCost by mutableStateOf("")
    var productInvalid by mutableStateOf(false)
    var quantityInvalid by mutableStateOf(false)
    var costInvalid by mutableStateOf(false)
}

private fun lineAmount(line: LineDraft): Long {
    val quantity = line.quantity.toLongOrNull() ?: return 0L
    val unitCost = line.unitCost.toLongOrNull() ?: return 0L
    return if (quantity > 0L && unitCost > 0L) quantity * unitCost else 0L
}

private fun rejectionMessage(reason: PurchaseRejection): String = when (reason) {
    PurchaseRejection.SupplierNotFound -> "Fournisseur introuvable."
    PurchaseRejection.NoItems -> "Ajoutez au moins un produit."
    PurchaseRejection.InvalidItem -> "Quantité ou prix invalide sur une ligne."
    PurchaseRejection.UnknownProduct -> "Un produit n'existe plus. Rechargez la page."
    PurchaseRejection.DuplicateProduct -> "Un même produit apparaît deux fois : regroupez les lignes."
    PurchaseRejection.InvalidPaid -> "Montant payé invalide (entre 0 et le total)."
    PurchaseRejection.TooHigh -> "Quantité ou montant trop grand."
    PurchaseRejection.Storage -> "Impossible d'enregistrer l'achat."
}

/** Lien de l'en-tête (.header-link) : « Achats » ↔ « Fournisseurs ». */
@Composable
fun HeaderLink(text: String, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Text(
        text,
        modifier = Modifier
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        color = c.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
    )
}

@Composable
fun PurchasesScreen(nav: NavController, requestedSupplier: String?) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val listState = rememberLazyListState()
    val collator = remember { Collator.getInstance(Locale.FRENCH) }

    var data by remember { mutableStateOf(PurchaseFormData(emptyList(), emptyList())) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        data = withContext(Dispatchers.Default) {
            PurchaseFormData(app.suppliers.getSuppliers(), app.products.getProducts())
        }
        loaded = true
    }

    val supplierOptions = remember(data.suppliers) {
        listOf("" to "Choisir un fournisseur…") +
            data.suppliers
                .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                .map { it.id to it.name }
    }
    val productOptions = remember(data.products) {
        listOf("" to "Choisir un produit…") +
            data.products
                .sortedWith(Comparator { a, b -> collator.compare(a.name, b.name) })
                .map { it.id to it.name }
    }

    var supplierId by rememberSaveable { mutableStateOf(requestedSupplier ?: "") }
    var supplierInvalid by remember { mutableStateOf(false) }
    var nextLineId by remember { mutableIntStateOf(1) }
    val lines = remember { mutableStateListOf(LineDraft(0)) }
    var updatePrices by remember { mutableStateOf(true) }
    var paidText by remember { mutableStateOf("") }
    var paidTouched by remember { mutableStateOf(false) }
    var paidInvalid by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    // Un fournisseur supprimé entre-temps n'est plus sélectionné.
    val selectedSupplier = if (data.suppliers.any { it.id == supplierId }) supplierId else ""

    val total = lines.sumOf { lineAmount(it) }
    // Tant que le champ « payé » n'est pas touché, il suit le total.
    val paidShown = if (paidTouched) paidText else if (total > 0L) total.toString() else ""
    val paidValue = if (paidShown.isBlank()) 0L else paidShown.toLongOrNull()

    val dueNote = if (paidValue != null && paidValue in 0L..total) {
        if (total - paidValue > 0L) {
            "Reste dû au fournisseur : " + formatMoney((total - paidValue).toDouble()) + "."
        } else {
            "Achat payé en totalité."
        }
    } else {
        "Le montant payé doit être compris entre 0 et le total."
    }

    val blocked = when {
        !loaded -> ""
        data.suppliers.isEmpty() -> "Ajoutez d'abord un fournisseur dans la page Fournisseurs."
        data.products.isEmpty() -> "Ajoutez d'abord des produits : un achat augmente leur stock."
        else -> ""
    }

    fun addLine() {
        if (lines.size >= MAX_LINES) {
            toast.show("Maximum 50 produits par achat.")
            return
        }
        lines.add(LineDraft(nextLineId))
        nextLineId += 1
    }

    fun removeLine(line: LineDraft) {
        if (lines.size <= 1) {
            toast.show("Un achat doit contenir au moins un produit.")
            return
        }
        lines.remove(line)
    }

    fun chooseProduct(line: LineDraft, id: String) {
        line.productId = id
        line.productInvalid = false
        // Choisir un produit propose son dernier prix d'achat.
        if (line.unitCost.isBlank()) {
            val chosen = data.products.firstOrNull { it.id == id }
            if (chosen != null) line.unitCost = plainNumber(chosen.purchasePrice)
        }
    }

    fun resetForm() {
        lines.clear()
        lines.add(LineDraft(nextLineId))
        nextLineId += 1
        paidTouched = false
        paidText = ""
        paidInvalid = false
        note = ""
        updatePrices = true
    }

    fun submit() {
        if (selectedSupplier.isEmpty()) {
            supplierInvalid = true
            toast.show("Choisissez un fournisseur.")
            return
        }

        val items = ArrayList<PurchaseLine>()
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
            val cost = line.unitCost.toLongOrNull()
            if (cost == null || cleanAmount(cost.toDouble()) == null) {
                line.costInvalid = true
                toast.show("Prix d'achat invalide.")
                return
            }
            items.add(PurchaseLine(line.productId, quantity.toInt(), cost.toDouble()))
        }

        val paid = paidValue
        if (paid == null || paid < 0L || paid > total) {
            paidInvalid = true
            toast.show("Montant payé invalide (entre 0 et le total).")
            return
        }

        val cleanNote = note.replace(JS_WHITESPACE_RUN, " ").jsTrim()
        if (cleanNote.length > ModuleLimits.TEXT_MAX) {
            toast.show("Note trop longue (${ModuleLimits.TEXT_MAX} caractères max).")
            return
        }

        // Avertit si la caisse devient négative (l'achat reste possible).
        val goesNegative = paid > 0L && app.cash.getCashSummary().balance - paid.toDouble() < 0.0

        when (
            val result = app.purchases.recordPurchase(
                PurchaseInput(selectedSupplier, items, paid.toDouble(), cleanNote, updatePrices)
            )
        ) {
            is RecordPurchaseResult.Rejected -> toast.show(rejectionMessage(result.reason))
            is RecordPurchaseResult.Ok -> {
                val due = total - paid
                toast.show(
                    if (goesNegative) "Achat enregistré. Attention : le solde de la caisse est négatif."
                    else if (due > 0L) "Achat enregistré. Reste dû : " + formatMoney(due.toDouble()) + ". Stock mis à jour."
                    else "Achat enregistré. Stock mis à jour."
                )
                resetForm()
                reloadKey += 1
            }
        }
    }

    ModuleScaffold(
        "GESTION", "Achats", { nav.goBack() }, toast, listState,
        headerAction = { HeaderLink("Fournisseurs") { nav.backTo(Routes.SUPPLIERS) } }
    ) {
        item {
            ModuleSection("Nouvel achat") {
                if (blocked.isNotEmpty()) {
                    Text(blocked, color = c.textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                } else {
                    ModuleField("Fournisseur") {
                        InvalidFrame(supplierInvalid) {
                            ModuleSelect(selectedSupplier, supplierOptions) {
                                supplierId = it
                                supplierInvalid = false
                            }
                        }
                    }

                    ModuleDetailTitle("Produits livrés")

                    for (line in lines) {
                        key(line.id) {
                            LineCard(
                                line = line,
                                productOptions = productOptions,
                                onChooseProduct = { chooseProduct(line, it) },
                                onRemove = { removeLine(line) }
                            )
                        }
                    }

                    ModuleActionButton(
                        "＋ Ajouter un produit", ModuleButtonStyle.Normal, Modifier.padding(top = 10.dp)
                    ) { addLine() }

                    CheckRow("Mettre à jour le prix d'achat des produits", updatePrices) {
                        updatePrices = !updatePrices
                    }

                    Row(
                        Modifier
                            .padding(top = 16.dp, bottom = 12.dp)
                            .fillMaxWidth()
                            .background(c.primarySoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total de l'achat", color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(formatMoney(total.toDouble()), color = c.primary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }

                    ModuleField("Payé maintenant (FCFA)") {
                        ModuleTextField(
                            paidShown,
                            {
                                paidTouched = true
                                paidText = it.filter { ch -> ch.isDigit() }
                                paidInvalid = false
                            },
                            "0",
                            keyboardType = KeyboardType.Number,
                            invalid = paidInvalid,
                            maxLength = 12
                        )
                    }
                    Text(
                        dueNote,
                        modifier = Modifier.padding(top = 6.dp),
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(Modifier.height(12.dp))
                    ModuleField("Note (facultatif)") {
                        ModuleTextField(
                            note, { note = it }, "Ex. : facture n° 245",
                            maxLength = 80, imeAction = ImeAction.Done
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Enregistrer l'achat", Modifier.fillMaxWidth()) { submit() }
                }
            }
        }
    }
}

/** Entoure un champ de liste d'un liseré rouge quand il est en erreur. */
@Composable
private fun InvalidFrame(invalid: Boolean, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(if (invalid) Modifier.border(1.dp, Ejden.colors.error, shape) else Modifier) { content() }
}

@Composable
private fun LineCard(
    line: LineDraft,
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
            InvalidFrame(line.productInvalid) {
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
                        "Ex. : 24",
                        keyboardType = KeyboardType.Number,
                        invalid = line.quantityInvalid,
                        maxLength = 7
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                ModuleField("Prix d'achat unitaire") {
                    ModuleTextField(
                        line.unitCost,
                        {
                            line.unitCost = it.filter { ch -> ch.isDigit() }
                            line.costInvalid = false
                        },
                        "FCFA",
                        keyboardType = KeyboardType.Number,
                        invalid = line.costInvalid,
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
                formatMoney(lineAmount(line).toDouble()),
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

/** Case à cocher avec son libellé (.check-row). */
@Composable
private fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(5.dp)
    Row(
        Modifier.padding(top = 14.dp).fillMaxWidth().tap(onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(20.dp)
                .background(if (checked) c.primary else c.surface, shape)
                .border(1.dp, if (checked) c.primary else c.border, shape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) StrokeIcon(IconSpec(listOf("M5 13l4 4L19 7")), Color.White, 14.dp, strokeWidth = 2.5f)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = c.text, fontSize = 13.sp, fontWeight = FontWeight(600))
    }
}
