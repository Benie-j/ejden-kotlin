package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.Product
import com.ejden.app.data.STOCK_REASONS
import com.ejden.app.data.STOCK_TAB_LABELS
import com.ejden.app.data.StockFilter
import com.ejden.app.data.StockHistoryEntry
import com.ejden.app.data.StockMovementResult
import com.ejden.app.data.StockMovementType
import com.ejden.app.data.StockStatus
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.filterStockProducts
import com.ejden.app.data.getStockValue
import com.ejden.app.data.jsTrim
import com.ejden.app.data.signedQuantity
import com.ejden.app.data.stockDateTime
import com.ejden.app.data.stockErrorMessage
import com.ejden.app.data.stockStatusOf
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId

private val QUANTITY_LABELS = mapOf(
    StockMovementType.In to "Quantité reçue",
    StockMovementType.Out to "Quantité sortie",
    StockMovementType.Adjust to "Quantité comptée en rayon"
)

@Composable
fun StockScreen(nav: NavController, initialFilter: String?) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()
    val zone = remember { ZoneId.systemDefault() }

    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var recent by remember { mutableStateOf<List<StockHistoryEntry>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(resumeKey, reloadKey) {
        val data = withContext(Dispatchers.Default) { app.products.getProducts() to app.stock.getHistory().take(15) }
        products = data.first
        recent = data.second
        loaded = true
    }

    var filter by rememberSaveable { mutableStateOf(StockFilter.fromId(initialFilter) ?: StockFilter.All) }
    var query by rememberSaveable { mutableStateOf("") }
    // Fiche ouverte : elle reste ouverte après chaque enregistrement.
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    val value = remember(products) { getStockValue(products) }
    val lowCount = remember(products) { products.count { stockStatusOf(it) == StockStatus.Low } }
    val outCount = remember(products) { products.count { stockStatusOf(it) == StockStatus.Out } }
    val filtered = remember(products, filter, query) { filterStockProducts(products, query, filter) }

    ModuleScaffold("GESTION", "Stock", { nav.goBack() }, toast) {
        item {
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryCard(
                    "Valeur du stock (prix d'achat)", formatNumber(value.cost), Modifier.fillMaxWidth(),
                    unit = "FCFA · ${products.size} produit" + (if (products.size > 1) "s" else "") +
                        " · valeur de vente ${formatNumber(value.retail)}",
                    wide = true
                )
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryCard("Stock faible", lowCount.toString(), Modifier.weight(1f).fillMaxHeight(), unit = "produits")
                    SummaryCard("Ruptures", outCount.toString(), Modifier.weight(1f).fillMaxHeight(), unit = "produits")
                }
            }
        }

        item { ModuleSearch(query, { query = it }, "Rechercher un produit...") }

        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill("Tous", filter == StockFilter.All) { filter = StockFilter.All }
                FilterPill("Stock faible", filter == StockFilter.Low) { filter = StockFilter.Low }
                FilterPill("Ruptures", filter == StockFilter.Out) { filter = StockFilter.Out }
            }
        }

        if (loaded && filtered.isEmpty()) {
            item {
                val none = products.isEmpty()
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val c = Ejden.colors
                    Text(
                        if (none) "Aucun produit" else "Aucun résultat",
                        color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (none) "Ajoutez des produits pour suivre votre stock."
                        else "Aucun produit ne correspond à votre recherche ou à ce filtre.",
                        modifier = Modifier.padding(top = 6.dp),
                        color = c.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                    if (none) {
                        Box(
                            Modifier
                                .padding(top = 14.dp)
                                .height(44.dp)
                                .background(c.primary, RoundedCornerShape(10.dp))
                                .tap { nav.open(Routes.PRODUCT_ADD) }
                                .padding(horizontal = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Ajouter un produit", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight(600))
                        }
                    }
                }
            }
        }

        items(filtered, key = { it.id }) { product ->
            StockCard(
                product = product,
                expanded = openId == product.id,
                zone = zone,
                reloadKey = reloadKey,
                onToggle = { openId = if (openId == product.id) null else product.id },
                onEdit = { nav.open(Routes.productEdit(product.id)) },
                onMovement = { type, quantity, reason ->
                    when (val result = app.stock.recordMovement(product.id, type, quantity, reason)) {
                        is StockMovementResult.Failed -> {
                            toast.show(stockErrorMessage(result.reason))
                            false
                        }
                        is StockMovementResult.Ok -> {
                            val after = result.movement.after
                            toast.show(
                                if (type == StockMovementType.Adjust) "Inventaire enregistré : $after en stock."
                                else "${STOCK_TAB_LABELS[type]} enregistrée : $after en stock."
                            )
                            openId = product.id
                            reloadKey += 1
                            true
                        }
                    }
                },
                history = { app.stock.getHistory(product.id).take(5) }
            )
        }

        if (recent.isNotEmpty()) {
            item {
                ModuleSection("Derniers mouvements") {
                    recent.forEachIndexed { index, entry ->
                        MovementRow(entry, withName = true, first = index == 0, zone = zone)
                    }
                }
            }
        }
    }
}

@Composable
private fun StockCard(
    product: Product,
    expanded: Boolean,
    zone: ZoneId,
    reloadKey: Int,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onMovement: (StockMovementType, Double, String) -> Boolean,
    history: () -> List<StockHistoryEntry>
) {
    val c = Ejden.colors
    val status = stockStatusOf(product)

    RowCard(
        expanded = expanded,
        onToggle = onToggle,
        summary = {
            RowMain(
                product.name,
                if (product.stockThreshold > 0) "Seuil d'alerte : ${product.stockThreshold}" else "Pas de seuil d'alerte",
                Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(product.stock.toString(), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(" en stock", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight(500), maxLines = 1)
                }
                when (status) {
                    StockStatus.Out -> Badge("Rupture", BadgeTone.Overdue)
                    StockStatus.Low -> Badge("Stock faible", BadgeTone.Debt)
                    StockStatus.Normal -> Badge("OK", BadgeTone.Success)
                }
            }
        },
        details = {
            ModuleDetailRow("Prix d'achat", formatMoney(product.purchasePrice))
            ModuleDetailRow("Prix de vente", formatMoney(product.salePrice))
            ModuleDetailRow("Valeur du stock", formatMoney(product.stock * product.purchasePrice))

            var activeType by remember(product.id) { mutableStateOf<StockMovementType?>(null) }

            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (type in StockMovementType.entries) {
                    val active = activeType == type
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 42.dp)
                            .background(if (active) c.primarySoft else c.surface, shape)
                            .border(1.dp, if (active) c.primary else c.border, shape)
                            // Un second appui referme le formulaire.
                            .tap { activeType = if (active) null else type },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            STOCK_TAB_LABELS.getValue(type),
                            color = if (active) c.primary else c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            val type = activeType
            if (type != null) {
                MovementForm(product, type) { quantity, reason -> onMovement(type, quantity, reason) }
            }

            val entries = remember(product.id, reloadKey) { history() }
            if (entries.isNotEmpty()) {
                ModuleDetailTitle("Derniers mouvements")
                entries.forEachIndexed { index, entry ->
                    MovementRow(entry, withName = false, first = index == 0, zone = zone)
                }
            }

            Text(
                "Modifier le produit",
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth().tap(onEdit),
                color = c.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                textAlign = TextAlign.Center
            )
        }
    )
}

@Composable
private fun MovementForm(product: Product, type: StockMovementType, onSubmit: (Double, String) -> Boolean) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var quantity by remember(type) { mutableStateOf("") }
    var reason by remember(type) { mutableStateOf(STOCK_REASONS.getValue(type).first()) }
    var note by remember(type) { mutableStateOf("") }
    var invalid by remember(type) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    fun focusQuantity() {
        try {
            focus.requestFocus()
        } catch (e: IllegalStateException) {
            // champ pas encore affiché
        }
    }

    LaunchedEffect(type) { focusQuantity() }

    Column(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField(QUANTITY_LABELS.getValue(type)) {
            ModuleTextField(
                quantity,
                { quantity = it.filter { ch -> ch.isDigit() }; invalid = false },
                when (type) {
                    StockMovementType.Out -> "Max ${product.stock}"
                    StockMovementType.Adjust -> "Actuellement ${product.stock}"
                    StockMovementType.In -> "Ex. : 24"
                },
                keyboardType = KeyboardType.Number,
                invalid = invalid,
                focusRequester = focus
            )
        }
        Spacer(Modifier.height(12.dp))
        ModuleField("Motif") {
            ModuleSelect(reason, STOCK_REASONS.getValue(type).map { it to it }) { reason = it }
        }
        Spacer(Modifier.height(12.dp))
        ModuleField("Précision (facultatif)") {
            ModuleTextField(note, { note = it }, "Ex. : livraison du lundi", maxLength = 40)
        }
        if (type == StockMovementType.Adjust) {
            Text(
                "Entrez la quantité réellement comptée : le stock sera remplacé par ce chiffre.",
                modifier = Modifier.padding(top = 6.dp),
                color = c.textSecondary,
                fontSize = 11.sp
            )
        }
        PrimaryButton("Enregistrer", Modifier.padding(top = 14.dp).fillMaxWidth()) {
            val raw = quantity.jsTrim()
            val amount = if (raw.isEmpty()) Double.NaN else (raw.toDoubleOrNull() ?: Double.NaN)
            val cleanNote = note.replace(JS_WHITESPACE_RUN, " ").jsTrim()
            val fullReason = if (cleanNote.isNotEmpty()) "$reason \u2014 $cleanNote" else reason
            if (!onSubmit(amount, fullReason)) {
                invalid = true
                focusQuantity()
            }
        }
    }
}

@Composable
private fun MovementRow(entry: StockHistoryEntry, withName: Boolean, first: Boolean, zone: ZoneId) {
    val c = Ejden.colors
    Column {
        if (!first) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (withName) entry.productName else entry.label,
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (withName) entry.label + " · " + stockDateTime(entry.createdAt, zone)
                    else stockDateTime(entry.createdAt, zone),
                    modifier = Modifier.padding(top = 2.dp),
                    color = c.textSecondary,
                    fontSize = 11.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    signedQuantity(entry.delta),
                    color = if (entry.delta > 0.0) c.success else c.error,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (entry.after != null) {
                    Text("stock : ${entry.after}", color = c.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}
