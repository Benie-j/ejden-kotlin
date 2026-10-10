package com.ejden.app.ui.history

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.CancelCreditPaymentResult
import com.ejden.app.data.CancelSaleResult
import com.ejden.app.data.CREDIT_PAYMENT_METHODS
import com.ejden.app.data.CreditPayment
import com.ejden.app.data.CreditPaymentResult
import com.ejden.app.data.HistoryFilter
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.Sale
import com.ejden.app.data.buildReceipt
import com.ejden.app.data.computeRemainingMap
import com.ejden.app.data.filterSales
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.fullDateText
import com.ejden.app.data.groupSalesByDay
import com.ejden.app.data.jsTrim
import com.ejden.app.data.parseDateMillis
import com.ejden.app.data.paymentLabel
import com.ejden.app.data.saleMargin
import com.ejden.app.data.saleNumber
import com.ejden.app.data.shortDateText
import com.ejden.app.data.summarizeHistory
import com.ejden.app.data.timeText
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.pressable
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private val FILTERS = listOf(
    HistoryFilter.All to "Toutes",
    HistoryFilter.Today to "Aujourd'hui",
    HistoryFilter.Paid to "Payées",
    HistoryFilter.Credit to "À crédit",
    HistoryFilter.Cancelled to "Annulées"
)

private sealed class HistoryRow(val key: String) {
    class Day(val label: String, key: String) : HistoryRow(key)
    class SaleRow(val sale: Sale, key: String) : HistoryRow(key)
}

private val CHEVRON = IconSpec(listOf("M9 6l6 6-6 6"))

@Composable
fun HistoryScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val resumeKey = rememberResumeKey()
    val zone = remember { ZoneId.systemDefault() }

    var sales by remember { mutableStateOf<List<Sale>>(emptyList()) }
    var payments by remember { mutableStateOf<List<CreditPayment>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(resumeKey, reloadKey) {
        val data = withContext(Dispatchers.Default) { app.sales.getSales() to app.credits.getCreditPayments() }
        sales = data.first
        payments = data.second
        loaded = true
    }

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(HistoryFilter.All) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    var toastText by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var toastToken by remember { mutableIntStateOf(0) }

    fun toast(message: String) {
        toastText = message
        toastVisible = true
        toastToken += 1
    }
    LaunchedEffect(toastToken) {
        if (toastToken > 0) {
            delay(3000)
            toastVisible = false
        }
    }

    /** Après une action : tout est relu et les cartes se referment (comme l'original). */
    fun reload() {
        reloadKey += 1
        expanded = emptySet()
    }

    val today = LocalDate.now()
    val remaining = remember(sales, payments) { computeRemainingMap(sales, payments) }
    val search = query.jsTrim().lowercase()
    val filtered = remember(sales, remaining, filter, search, today) {
        filterSales(sales, remaining, filter, search, today, zone)
    }
    val summary = remember(filtered, remaining) { summarizeHistory(filtered, remaining) }
    val rows = remember(filtered, today) {
        val result = ArrayList<HistoryRow>()
        var index = 0
        for (group in groupSalesByDay(filtered, today, zone)) {
            result.add(HistoryRow.Day(group.label, "day:" + group.key + ":" + index))
            for (sale in group.sales) {
                result.add(HistoryRow.SaleRow(sale, "sale:" + index + ":" + sale.id))
                index += 1
            }
            index += 1
        }
        result
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxHeight().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 32.dp + navInset)
        ) {
            item {
                HistoryHeader(
                    onBack = { nav.goBack() },
                    onNewSale = { nav.open(Routes.SALES) }
                )
            }
            item { SearchField(query) { query = it.take(80) } }
            item {
                Row(
                    Modifier
                        .padding(top = 12.dp, bottom = 14.dp)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((value, label) in FILTERS) FilterButton(label, filter == value) { filter = value }
                }
            }
            item { SummaryRow(summary.count, summary.total, summary.remaining) }

            if (loaded && filtered.isEmpty()) {
                item {
                    EmptyHistory(
                        title = if (sales.isEmpty()) "Aucune vente" else "Aucun résultat",
                        text = if (sales.isEmpty()) "Les ventes enregistrées apparaîtront ici."
                        else "Aucune vente ne correspond à votre recherche ou à ce filtre.",
                        onNewSale = { nav.open(Routes.SALES) }
                    )
                }
            }

            items(rows, key = { it.key }) { row ->
                when (row) {
                    is HistoryRow.Day -> Text(
                        row.label,
                        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 18.dp, bottom = 8.dp),
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    is HistoryRow.SaleRow -> {
                        val sale = row.sale
                        val salePayments = remember(payments, sale.id) {
                            payments.filter { it.saleId == sale.id }
                                .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
                        }
                        SaleCard(
                            sale = sale,
                            remaining = remaining[sale.id] ?: 0.0,
                            salePayments = salePayments,
                            expanded = sale.id in expanded,
                            zone = zone,
                            onToggle = {
                                expanded = if (sale.id in expanded) expanded - sale.id else expanded + sale.id
                            },
                            onShare = {
                                val shop = app.settings.getShop()
                                val remainingNow = computeRemainingMap(listOf(sale), app.credits.getCreditPayments())[sale.id] ?: 0.0
                                val text = buildReceipt(sale, shop, remainingNow, zone)
                                if (!shareText(context, text)) toast("Impossible de partager le reçu.")
                            },
                            onTicket = { toast("Ticket : bientôt disponible.") },
                            onCancelSale = {
                                val message = when (val result = app.sales.cancelSale(sale.id)) {
                                    is CancelSaleResult.Ok ->
                                        if (result.missingProducts > 0) {
                                            "Vente annulée. Certains produits supprimés n'ont pas pu être remis en stock."
                                        } else {
                                            "Vente annulée. Le stock a été remis."
                                        }
                                    CancelSaleResult.AlreadyCancelled -> "Cette vente est déjà annulée."
                                    CancelSaleResult.NotFound -> "Vente introuvable."
                                    CancelSaleResult.HasPayments ->
                                        "Des paiements ont déjà été reçus sur ce crédit. Annulez-les d'abord."
                                    CancelSaleResult.StorageError -> "Impossible d'annuler la vente."
                                }
                                toast(message)
                                reload()
                            },
                            onPay = { amount, method, due ->
                                when (val result = app.credits.recordCreditPayment(listOf(sale.id), amount.toDouble(), method)) {
                                    is CreditPaymentResult.Ok -> {
                                        toast(
                                            if (amount.toDouble() == due) "Crédit soldé : ${formatMoney(amount.toDouble())} reçus."
                                            else "Paiement de ${formatMoney(amount.toDouble())} enregistré. Reste ${formatMoney(due - amount)}."
                                        )
                                        reload()
                                        true
                                    }
                                    is CreditPaymentResult.TooMuch -> {
                                        toast("Ce montant dépasse ce qui est dû (${formatMoney(result.due)}).")
                                        false
                                    }
                                    CreditPaymentResult.NoDebt -> {
                                        toast("Ce crédit est déjà soldé.")
                                        false
                                    }
                                    CreditPaymentResult.StorageError -> {
                                        toast("Enregistrement impossible : la mémoire est pleine.")
                                        false
                                    }
                                    else -> {
                                        toast("Paiement invalide.")
                                        false
                                    }
                                }
                            },
                            onPayError = { toast(it) },
                            onCancelPayment = { paymentId ->
                                if (app.credits.cancelCreditPayment(paymentId) == CancelCreditPaymentResult.Ok) {
                                    toast("Paiement annulé.")
                                } else {
                                    toast("Impossible d'annuler ce paiement.")
                                }
                                reload()
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            toastVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp + navInset),
            enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { 12 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { 12 }
        ) {
            Text(
                toastText,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .background(c.text, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                color = c.surface,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun shareText(context: Context, text: String): Boolean = try {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, null))
    true
} catch (e: Exception) {
    false
}

// ---------------------------------------------------------------------------
// En-tête, recherche, filtres, résumé, état vide
// ---------------------------------------------------------------------------

@Composable
private fun HistoryHeader(onBack: () -> Unit, onNewSale: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.padding(bottom = 16.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(44.dp).tap(onBack).background(c.surface, shape).border(1.dp, c.border, shape),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(ProductIcons.Back, c.text, 20.dp, strokeWidth = 2f)
        }
        Column(Modifier.weight(1f)) {
            Text(
                "COMMERCE",
                color = c.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em
            )
            Text(
                "Historique des ventes",
                color = c.text,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            Modifier.size(44.dp).tap(onNewSale).background(c.primary, shape).border(1.dp, c.primary, shape),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(AppIcons.Plus, Color.White, 20.dp, strokeWidth = 2f)
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else c.border, shape)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        // Couleur du texte indicatif par défaut du navigateur, comme l'original.
                        Text("Produit, client ou n° de vente...", color = Color(0xFF757575), fontSize = 14.sp)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun FilterButton(label: String, active: Boolean, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .height(38.dp)
            .background(if (active) c.primary else c.surface, CircleShape)
            .border(1.dp, if (active) c.primary else c.border, CircleShape)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) Color.White else c.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight(600)
        )
    }
}

@Composable
private fun SummaryRow(count: Int, total: Double, remaining: Double) {
    Row(
        Modifier.padding(bottom = 18.dp).fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SummaryCell("Ventes", count.toString(), null, Modifier.weight(1f))
        SummaryCell("Total", formatNumber(total), "FCFA", Modifier.weight(1f))
        SummaryCell("Reste dû", formatNumber(remaining), "FCFA", Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, unit: String?, modifier: Modifier) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.fillMaxHeight().background(c.surface, shape).border(1.dp, c.border, shape).padding(12.dp)
    ) {
        Text(label, color = c.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            color = c.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (unit != null) Text(unit, color = c.textSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun EmptyHistory(title: String, text: String, onNewSale: () -> Unit) {
    val c = Ejden.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            text,
            modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            color = c.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .height(44.dp)
                .background(c.primary, RoundedCornerShape(10.dp))
                .tap(onNewSale)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Nouvelle vente", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight(600))
        }
    }
}

// ---------------------------------------------------------------------------
// Carte d'une vente
// ---------------------------------------------------------------------------

@Composable
private fun SaleCard(
    sale: Sale,
    remaining: Double,
    salePayments: List<CreditPayment>,
    expanded: Boolean,
    zone: ZoneId,
    onToggle: () -> Unit,
    onShare: () -> Unit,
    onTicket: () -> Unit,
    onCancelSale: () -> Unit,
    onPay: (Int, PaymentMethod, Double) -> Boolean,
    onPayError: (String) -> Unit,
    onCancelPayment: (String) -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    val isCancelled = sale.cancelledAt != null
    val isCredit = !isCancelled && remaining > 0.0
    val quantity = sale.items.sumOf { it.quantity }.toInt()
    val first = sale.items.firstOrNull()?.productName ?: "Vente"
    val others = sale.items.size - 1
    val chevronAngle by animateFloatAsState(if (expanded) 90f else 0f, tween(200), label = "chevron")

    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .clip16()
    ) {
        // --- Résumé, toujours visible ---
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .tap(onToggle)
                .alpha(if (isCancelled) 0.6f else 1f)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (others > 0) "$first +$others" else first,
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    timeText(sale.createdAt, zone) + " · " + quantity + " article" + if (quantity > 1) "s" else "",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(sale.total),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(4.dp))
                val (bg, fg) = when {
                    isCancelled -> c.errorSoft to c.error
                    isCredit -> c.warningSoft to c.warning
                    else -> c.successSoft to c.success
                }
                Text(
                    if (isCancelled) "Annulée" else if (isCredit) "À crédit" else "Payée",
                    modifier = Modifier.background(bg, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                    color = fg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            StrokeIcon(CHEVRON, c.textSecondary, 16.dp, Modifier.rotate(chevronAngle), strokeWidth = 2f)
        }

        // --- Détail complet ---
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            SaleDetails(
                sale, remaining, salePayments, zone,
                isCancelled, isCredit,
                onShare, onTicket, onCancelSale, onPay, onPayError, onCancelPayment
            )
        }
    }
}

private fun Modifier.clip16(): Modifier = this.then(Modifier.clip(RoundedCornerShape(16.dp)))

@Composable
private fun SaleDetails(
    sale: Sale,
    remaining: Double,
    salePayments: List<CreditPayment>,
    zone: ZoneId,
    isCancelled: Boolean,
    isCredit: Boolean,
    onShare: () -> Unit,
    onTicket: () -> Unit,
    onCancelSale: () -> Unit,
    onPay: (Int, PaymentMethod, Double) -> Boolean,
    onPayError: (String) -> Unit,
    onCancelPayment: (String) -> Unit
) {
    val c = Ejden.colors

    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp)) {
        DetailTitle("Informations")
        DetailRow("Vente", saleNumber(sale))
        DetailRow("Date", fullDateText(sale.createdAt, zone))
        DetailRow("Client", sale.borrowerName ?: "Client de passage")
        if (sale.cancelledAt != null) {
            DetailRow("Annulée le", fullDateText(sale.cancelledAt, zone), valueColor = c.warning)
        }

        DetailTitle("Articles")
        sale.items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.productName, color = c.text, fontSize = 13.sp)
                    Text(
                        "${com.ejden.app.data.plainNumber(item.quantity)} × ${formatMoney(item.unitPrice)}",
                        modifier = Modifier.padding(top = 2.dp),
                        color = c.textSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(formatMoney(item.total), color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            if (index < sale.items.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }

        DetailTitle("Paiement")
        DetailRow("Sous-total", formatMoney(sale.subtotal))
        DetailRow("Remise", if (sale.discount > 0.0) "− " + formatMoney(sale.discount) else "Aucune")

        Column(Modifier.padding(top = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(formatMoney(sale.total), color = c.text, fontSize = 15.sp, fontWeight = FontWeight(600))
            }
        }

        DetailRow("Mode de paiement", paymentLabel(sale.paymentMethod))
        DetailRow("Montant payé", formatMoney(sale.amountPaid))
        if (sale.change > 0.0) DetailRow("Monnaie rendue", formatMoney(sale.change))

        if (sale.remaining > 0.0 && !isCancelled) {
            DetailRow("Crédit initial", formatMoney(sale.remaining))
            val paidBack = sale.remaining - remaining
            if (paidBack > 0.0) DetailRow("Déjà remboursé", formatMoney(paidBack))
            if (isCredit) {
                DetailRow("Reste à payer", formatMoney(remaining), valueColor = c.warning)
            } else {
                DetailRow("Crédit", "Soldé")
            }
        }

        if (salePayments.isNotEmpty() && !isCancelled && sale.remaining > 0.0) {
            DetailTitle("Paiements reçus")
            for (payment in salePayments) PaymentRow(payment, zone, onCancelPayment)
        }

        if (isCredit) PaymentForm(remaining, onPay, onPayError)

        val margin = saleMargin(sale)
        if (margin != null && !isCancelled) DetailRow("Bénéfice", formatMoney(margin))

        if (!isCancelled) SaleActions(onShare, onTicket, onCancelSale)
    }
}

@Composable
private fun DetailTitle(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        color = Ejden.colors.textSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em
    )
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color? = null) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = c.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            modifier = Modifier.weight(1f, fill = false),
            color = valueColor ?: c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight(600),
            textAlign = TextAlign.End
        )
    }
}

// ---------------------------------------------------------------------------
// Boutons
// ---------------------------------------------------------------------------

private enum class ButtonStyle { Primary, Normal, Danger, DangerSolid }

@Composable
private fun ActionButton(
    label: String,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    val (background, border, foreground) = when (style) {
        ButtonStyle.Primary -> Triple(c.primary, c.primary, Color.White)
        ButtonStyle.Normal -> Triple(c.surface, c.border, c.text)
        ButtonStyle.Danger -> Triple(c.surface, c.error, c.error)
        ButtonStyle.DangerSolid -> Triple(c.error, c.error, Color.White)
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .background(background, shape)
            .border(1.dp, border, shape)
            .tap(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = foreground, fontSize = 14.sp, fontWeight = FontWeight(600))
    }
}

@Composable
private fun SaleActions(onShare: () -> Unit, onTicket: () -> Unit, onCancelSale: () -> Unit) {
    val c = Ejden.colors
    var confirming by remember { mutableStateOf(false) }

    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActionButton("Partager le reçu", ButtonStyle.Primary, onClick = onShare)
        ActionButton("Ticket 80 mm", ButtonStyle.Normal, onClick = onTicket)
        ActionButton("Ticket 58 mm", ButtonStyle.Normal, onClick = onTicket)

        if (!confirming) {
            ActionButton("Annuler la vente", ButtonStyle.Danger) { confirming = true }
        } else {
            // Confirmation en deux temps : pas d'annulation par erreur.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.errorSoft, RoundedCornerShape(10.dp))
                    .border(1.dp, c.error, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Annuler cette vente ? Le stock des produits sera remis. Cette action est définitive.",
                    color = c.text,
                    fontSize = 13.sp,
                    lineHeight = 18.2.sp
                )
                ActionButton("Oui, annuler la vente", ButtonStyle.DangerSolid) { onCancelSale() }
                ActionButton("Non, garder", ButtonStyle.Normal) { confirming = false }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Paiements d'un crédit
// ---------------------------------------------------------------------------

@Composable
private fun PaymentRow(payment: CreditPayment, zone: ZoneId, onCancel: (String) -> Unit) {
    val c = Ejden.colors
    val cancelled = payment.cancelledAt != null
    var confirming by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    formatMoney(payment.amount),
                    color = if (cancelled) c.textSecondary else c.success,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null
                )
                Text(
                    shortDateText(payment.createdAt, zone) + " · " + timeText(payment.createdAt, zone) +
                        " · " + paymentLabel(payment.method) + if (cancelled) " · annulé" else "",
                    color = c.textSecondary,
                    fontSize = 12.sp
                )
            }

            if (!cancelled) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!confirming) {
                        Box(
                            Modifier
                                .heightIn(min = 36.dp)
                                .background(c.surface, RoundedCornerShape(10.dp))
                                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                                .tap { confirming = true }
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Annuler", color = c.error, fontSize = 12.sp, fontWeight = FontWeight(600))
                        }
                    }
                }
            }
        }

        if (confirming && !cancelled) {
            Column(
                Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
                    .border(1.dp, c.error, RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Annuler ce paiement de ${formatMoney(payment.amount)} ? Le reste à payer augmentera.",
                    color = c.text,
                    fontSize = 12.sp,
                    lineHeight = 16.8.sp
                )
                ActionButton("Oui, annuler", ButtonStyle.Danger) { onCancel(payment.id) }
                ActionButton("Non", ButtonStyle.Normal) { confirming = false }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

private fun Modifier.focusRing(visible: Boolean, color: Color, radius: Dp): Modifier = drawBehind {
    if (visible) {
        val ring = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-ring / 2, -ring / 2),
            size = Size(size.width + ring, size.height + ring),
            cornerRadius = CornerRadius(radius.toPx() + ring / 2),
            style = Stroke(ring)
        )
    }
}

@Composable
private fun PaymentForm(due: Double, onPay: (Int, PaymentMethod, Double) -> Boolean, onError: (String) -> Unit) {
    val c = Ejden.colors
    var open by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(CREDIT_PAYMENT_METHODS.first()) }
    var menuOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(Modifier.padding(top = 12.dp)) {
        if (!open) {
            ActionButton("Encaisser un paiement", ButtonStyle.Primary) { open = true }
            return@Column
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(10.dp))
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Montant reçu (FCFA)", color = c.text, fontSize = 13.sp, fontWeight = FontWeight(600))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var focused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .focusRing(focused, c.primary.copy(alpha = 0.12f), 12.dp)
                        .background(c.surface, shape)
                        .border(1.dp, if (focused) c.primary else c.border, shape)
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { ch -> ch.isDigit() } },
                        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                        singleLine = true,
                        textStyle = TextStyle(color = c.text, fontSize = 16.sp),
                        cursorBrush = SolidColor(c.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (amountText.isEmpty()) Text("0", color = Color(0xFF757575), fontSize = 16.sp)
                                inner()
                            }
                        }
                    )
                }
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .background(c.primarySoft, shape)
                        .border(1.dp, c.primary, shape)
                        .tap { amountText = com.ejden.app.data.plainNumber(due) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Tout solder (${formatNumber(due)})",
                        color = c.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            Text("Mode de paiement", color = c.text, fontSize = 13.sp, fontWeight = FontWeight(600))
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(c.surface, shape)
                        .border(1.dp, c.border, shape)
                        .tap { menuOpen = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(paymentLabel(method), color = c.text, fontSize = 16.sp)
                    StrokeIcon(IconSpec(listOf("M6 9l6 6 6-6")), c.textSecondary, 18.dp)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    for (option in CREDIT_PAYMENT_METHODS) {
                        DropdownMenuItem(
                            text = { Text(paymentLabel(option)) },
                            onClick = {
                                method = option
                                menuOpen = false
                            }
                        )
                    }
                }
            }

            // Reste après paiement.
            val amount = amountText.toLongOrNull()
            val over = amount != null && amount > 0 && amount.toDouble() > due
            Text(
                when {
                    amount == null || amount <= 0 -> "Reste dû : ${formatMoney(due)}"
                    over -> "Ce montant dépasse ce qui est dû (${formatMoney(due)})."
                    amount.toDouble() == due -> "Le crédit sera entièrement soldé."
                    else -> "Reste après paiement : ${formatMoney(due - amount)}"
                },
                color = if (over) c.error else c.textSecondary,
                fontSize = 13.sp,
                fontWeight = if (over) FontWeight(600) else FontWeight.Normal
            )

            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("Enregistrer le paiement", ButtonStyle.Primary) {
                    if (saving) return@ActionButton
                    val value = amountText.toLongOrNull()
                    if (value == null || value <= 0) {
                        onError("Saisissez un montant entier, en FCFA.")
                    } else {
                        saving = true
                        val saved = onPay(value.toInt(), method, due)
                        if (!saved) saving = false
                    }
                }
                ActionButton("Annuler", ButtonStyle.Normal) {
                    open = false
                    amountText = ""
                    method = CREDIT_PAYMENT_METHODS.first()
                }
            }
        }
    }
}
