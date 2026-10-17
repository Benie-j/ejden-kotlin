package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.EjdenApp
import com.ejden.app.data.CREDIT_PAYMENT_METHODS
import com.ejden.app.data.CompleteOrderResult
import com.ejden.app.data.Order
import com.ejden.app.data.OrderChangeResult
import com.ejden.app.data.OrderItem
import com.ejden.app.data.OrderKind
import com.ejden.app.data.OrderStatus
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.Product
import com.ejden.app.data.PurchaseRejection
import com.ejden.app.data.dayLabel
import com.ejden.app.data.formatMoney
import com.ejden.app.data.isOrderOverdue
import com.ejden.app.data.parseDateMillis
import com.ejden.app.data.paymentLabel
import com.ejden.app.data.plainNumber
import com.ejden.app.data.shortDateText
import com.ejden.app.data.timeText
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private sealed class OrderListRow(val key: String) {
    class Day(val label: String, key: String) : OrderListRow(key)
    class Card(val order: Order, key: String) : OrderListRow(key)
}

/** Liste des commandes : filtres, titres de jour (terminées/annulées) et cartes détaillées. */
internal fun LazyListScope.orderList(
    orders: List<Order>,
    products: List<Product>,
    kind: OrderKind,
    filterKey: String,
    onFilter: (String) -> Unit,
    openOrderId: String?,
    loaded: Boolean,
    toast: ModuleToastState,
    onChanged: () -> Unit
) {
    item { OrderFilterChips(filterKey, onFilter) }

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()

    val matching = orders.filter { order ->
        order.kind == kind && when (filterKey) {
            "completed" -> order.status == OrderStatus.Completed
            "cancelled" -> order.status == OrderStatus.Cancelled
            else -> order.isOpen
        }
    }

    // En cours : date prévue la plus proche d'abord, sinon la plus récente.
    val sorted = if (filterKey == "open") {
        matching.sortedWith(
            compareBy<Order> { it.expectedDate ?: "9999-12-31" }
                .thenByDescending { parseDateMillis(it.createdAt) ?: 0L }
        )
    } else {
        matching.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
    }

    if (loaded && sorted.isEmpty()) {
        val label = if (filterKey == "completed") "terminée" else "annulée"
        item {
            ModuleEmpty(
                if (filterKey == "open") "Aucune commande en cours" else "Aucune commande",
                if (filterKey == "open") "Les commandes que vous enregistrez apparaîtront ici."
                else "Aucune commande $label."
            )
        }
    }

    val rows = ArrayList<OrderListRow>()
    var lastDay: LocalDate? = null
    for (order in sorted) {
        // Terminées / annulées : regroupées par jour.
        if (filterKey != "open") {
            val reference = order.completedAt ?: order.cancelledAt ?: order.createdAt
            val millis = parseDateMillis(reference) ?: 0L
            val date = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            if (date != lastDay) {
                rows.add(OrderListRow.Day(dayLabel(date, today), "day:" + rows.size))
                lastDay = date
            }
        }
        rows.add(OrderListRow.Card(order, "order:" + order.id))
    }

    items(rows, key = { it.key }) { row ->
        when (row) {
            is OrderListRow.Day -> DayHeading(row.label)
            is OrderListRow.Card -> OrderCard(
                order = row.order,
                products = products,
                startOpen = row.order.id == openOrderId,
                toast = toast,
                onChanged = onChanged
            )
        }
    }
}

@Composable
private fun OrderFilterChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .padding(top = 4.dp, bottom = 14.dp)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OrderChip("En cours", selected == "open") { onSelect("open") }
        OrderChip("Terminées", selected == "completed") { onSelect("completed") }
        OrderChip("Annulées", selected == "cancelled") { onSelect("cancelled") }
    }
}

@Composable
private fun OrderChip(label: String, active: Boolean, onClick: () -> Unit) {
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
            fontWeight = FontWeight(600),
            maxLines = 1
        )
    }
}

private fun completionMessage(result: CompleteOrderResult): String = when (result) {
    is CompleteOrderResult.Ok -> ""
    CompleteOrderResult.NotFound -> "Commande introuvable."
    CompleteOrderResult.NotAllowed -> "Cette commande n'est plus en cours."
    CompleteOrderResult.InvalidAmount -> "Montant payé invalide (entre 0 et le total)."
    CompleteOrderResult.InvalidMethod -> "Mode de paiement invalide."
    CompleteOrderResult.CreditNeedsClient ->
        "Pour laisser un reste à payer, la commande doit être au nom d'un client enregistré."
    is CompleteOrderResult.OutOfStock -> "Stock insuffisant pour « " + result.productName + " »."
    is CompleteOrderResult.PurchaseRejected -> when (result.reason) {
        PurchaseRejection.SupplierNotFound -> "Ce fournisseur n'existe plus."
        PurchaseRejection.InvalidPaid -> "Montant payé invalide (entre 0 et le total)."
        PurchaseRejection.TooHigh -> "Le stock dépasserait la limite autorisée."
        else -> "Impossible d'enregistrer : rien n'a été modifié."
    }
    CompleteOrderResult.StorageError -> "Impossible d'enregistrer : rien n'a été modifié."
}

@Composable
private fun OrderStatusBadge(order: Order, overdue: Boolean) {
    val c = Ejden.colors
    when {
        order.status == OrderStatus.Completed ->
            Badge(if (order.kind == OrderKind.Customer) "Livrée" else "Reçue", BadgeTone.Success)
        order.status == OrderStatus.Cancelled -> Badge("Annulée", BadgeTone.Overdue)
        overdue -> Badge("En retard", BadgeTone.Overdue)
        order.status == OrderStatus.Ready ->
            Text(
                "Prête",
                modifier = Modifier.background(c.primarySoft, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                color = c.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        else -> Badge("En attente", BadgeTone.Debt)
    }
}

@Composable
private fun OrderCard(
    order: Order,
    products: List<Product>,
    startOpen: Boolean,
    toast: ModuleToastState,
    onChanged: () -> Unit
) {
    val c = Ejden.colors
    val app = LocalContext.current.applicationContext as EjdenApp
    val zone = remember { ZoneId.systemDefault() }
    var expanded by rememberSaveable(order.id) { mutableStateOf(startOpen) }
    var panelOpen by remember { mutableStateOf(false) }
    var cancelAttempt by remember { mutableIntStateOf(0) }

    val customer = order.kind == OrderKind.Customer
    val overdue = isOrderOverdue(order, Instant.now(), zone)
    val count = order.items.sumOf { it.quantity }
    val expected = order.expectedDate

    RowCard(
        expanded = expanded,
        onToggle = { expanded = !expanded },
        summary = {
            RowMain(
                order.partyName,
                count.toString() + " article" + (if (count > 1) "s" else "") +
                    (if (expected != null) " · pour le " + orderDayText(expected) else ""),
                Modifier.weight(1f)
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatMoney(order.total),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Box(Modifier.height(4.dp))
                OrderStatusBadge(order, overdue)
            }
        },
        details = {
            ModuleDetailTitle("Produits")
            for (item in order.items) {
                // Une commande client en cours signale le stock insuffisant.
                val shortage = if (customer && order.isOpen) {
                    val stock = products.firstOrNull { it.id == item.productId }?.stock ?: 0
                    if (stock < item.quantity) {
                        if (stock == 0) "en rupture" else "stock insuffisant ($stock)"
                    } else {
                        null
                    }
                } else {
                    null
                }
                OrderItemLine(item, shortage)
            }

            ModuleDetailTitle("Informations")
            ModuleDetailRow(
                "Commande du",
                shortDateText(order.createdAt, zone) + " " + timeText(order.createdAt, zone)
            )
            ModuleDetailRow("Total", formatMoney(order.total))
            if (expected != null) {
                ModuleDetailRow("Date prévue", orderDayText(expected), valueColor = if (overdue) c.error else null)
            }
            if (order.note.isNotEmpty()) {
                ModuleDetailRow("Note", order.note)
            }

            val completedAt = order.completedAt
            if (completedAt != null) {
                ModuleDetailRow(
                    if (customer) "Livrée le" else "Reçue le",
                    shortDateText(completedAt, zone) + " " + timeText(completedAt, zone)
                )
                val link = order.linkId
                ModuleDetailRow(
                    if (customer) "Vente créée" else "Achat créé",
                    if (link != null) "N° " + link.takeLast(6).uppercase() else "—"
                )
            }

            val cancelledAt = order.cancelledAt
            if (cancelledAt != null) {
                ModuleDetailRow("Annulée le", shortDateText(cancelledAt, zone) + " " + timeText(cancelledAt, zone))
            }

            if (order.isOpen) {
                Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleActionButton(
                        if (customer) "Livrer et encaisser" else "Réceptionner",
                        ModuleButtonStyle.Primary
                    ) { panelOpen = !panelOpen }

                    if (panelOpen) {
                        if (customer) {
                            DeliveryPanel(order) { method, amount ->
                                val result = app.orderCompletion.completeCustomerOrder(order.id, method, amount)
                                if (result is CompleteOrderResult.Ok) {
                                    toast.show("Commande livrée : vente enregistrée, stock mis à jour.")
                                    onChanged()
                                    true
                                } else {
                                    toast.show(completionMessage(result))
                                    false
                                }
                            }
                        } else {
                            ReceivePanel(order) { paid, updatePrices ->
                                val result = app.orderCompletion.receiveSupplierOrder(order.id, paid, updatePrices)
                                if (result is CompleteOrderResult.Ok) {
                                    toast.show("Commande reçue : achat enregistré, stock augmenté.")
                                    onChanged()
                                    true
                                } else {
                                    toast.show(completionMessage(result))
                                    false
                                }
                            }
                        }
                    }

                    if (customer && order.status == OrderStatus.Pending) {
                        ModuleActionButton("Marquer comme prête", ModuleButtonStyle.Normal) {
                            val ok = app.orders.markOrderReady(order.id) == OrderChangeResult.Ok
                            toast.show(if (ok) "Commande prête." else "Impossible de modifier cette commande.")
                            onChanged()
                        }
                    }

                    key(cancelAttempt) {
                        DeleteControl(
                            label = "Annuler la commande",
                            question = "Annuler cette commande ? Le stock et la caisse ne sont pas touchés " +
                                "(rien n'a encore été livré)."
                        ) {
                            val ok = app.orders.cancelOrder(order.id) == OrderChangeResult.Ok
                            toast.show(if (ok) "Commande annulée." else "Impossible d'annuler cette commande.")
                            cancelAttempt += 1
                            onChanged()
                        }
                    }
                }
            }
        }
    )
}

/** Une ligne de produit : nom, « 6 × 700 FCFA » (+ alerte de stock) et total. */
@Composable
private fun OrderItemLine(item: OrderItem, shortage: String?) {
    val c = Ejden.colors
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.productName, color = c.text, fontSize = 13.sp)
            Text(
                buildAnnotatedString {
                    append(item.quantity.toString() + " × " + formatMoney(item.unitPrice))
                    if (shortage != null) {
                        append(" · ")
                        withStyle(SpanStyle(color = c.error, fontWeight = FontWeight.Bold)) { append(shortage) }
                    }
                },
                modifier = Modifier.padding(top = 2.dp),
                color = c.textSecondary,
                fontSize = 11.sp
            )
        }
        Text(
            formatMoney(item.quantity * item.unitPrice),
            color = c.text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.End
        )
    }
}

/** Panneau « Livrer et encaisser » (commande client). */
@Composable
private fun DeliveryPanel(order: Order, onConfirm: (PaymentMethod, Double) -> Boolean) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var amountText by remember { mutableStateOf(plainNumber(order.total)) }
    var method by remember { mutableStateOf(CREDIT_PAYMENT_METHODS.first()) }
    var invalid by remember { mutableStateOf(false) }

    val paid = amountText.toLongOrNull()
    val noteText = when {
        paid == null || paid < 0L || paid.toDouble() > order.total ->
            "Le montant payé doit être compris entre 0 et le total."
        paid.toDouble() < order.total ->
            if (order.partyId == null) {
                "Un reste à payer exige un client enregistré."
            } else {
                "Le reste (" + formatMoney(order.total - paid.toDouble()) + ") deviendra un crédit de " +
                    order.partyName + "."
            }
        else -> "Vente payée en totalité."
    }

    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField("Montant payé maintenant (FCFA)") {
            ModuleTextField(
                amountText,
                { amountText = it.filter { ch -> ch.isDigit() }; invalid = false },
                "",
                keyboardType = KeyboardType.Number,
                invalid = invalid,
                maxLength = 12,
                imeAction = ImeAction.Done
            )
        }
        Spacer(Modifier.height(12.dp))
        ModuleField("Mode de paiement") {
            ModuleSelect(method.id, CREDIT_PAYMENT_METHODS.map { it.id to paymentLabel(it) }) { id ->
                val chosen = PaymentMethod.fromId(id)
                if (chosen != null) method = chosen
            }
        }
        Text(
            noteText,
            modifier = Modifier.padding(top = 10.dp),
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))
        ModuleActionButton("Confirmer la livraison", ModuleButtonStyle.Primary) {
            val amount = amountText.toLongOrNull()?.toDouble() ?: Double.NaN
            if (!onConfirm(method, amount)) invalid = true
        }
    }
}

/** Panneau « Réceptionner » (commande fournisseur). */
@Composable
private fun ReceivePanel(order: Order, onConfirm: (Double, Boolean) -> Boolean) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var paidText by remember { mutableStateOf(plainNumber(order.total)) }
    var updatePrices by remember { mutableStateOf(true) }
    var invalid by remember { mutableStateOf(false) }

    val paid = paidText.toLongOrNull()
    val noteText = when {
        paid == null || paid < 0L || paid.toDouble() > order.total ->
            "Le montant payé doit être compris entre 0 et le total."
        paid.toDouble() < order.total ->
            "Le reste (" + formatMoney(order.total - paid.toDouble()) + ") sera une dette envers " +
                order.partyName + "."
        else -> "Achat payé en totalité."
    }

    Column(
        Modifier
            .padding(top = 4.dp)
            .fillMaxWidth()
            .background(c.background, shape)
            .border(1.dp, c.border, shape)
            .padding(12.dp)
    ) {
        ModuleField("Payé maintenant (FCFA)") {
            ModuleTextField(
                paidText,
                { paidText = it.filter { ch -> ch.isDigit() }; invalid = false },
                "",
                keyboardType = KeyboardType.Number,
                invalid = invalid,
                maxLength = 12,
                imeAction = ImeAction.Done
            )
        }
        OrderCheckRow("Mettre à jour le prix d'achat des produits", updatePrices) { updatePrices = !updatePrices }
        Text(
            noteText,
            modifier = Modifier.padding(top = 10.dp),
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(12.dp))
        ModuleActionButton("Confirmer la réception", ModuleButtonStyle.Primary) {
            val amount = paidText.toLongOrNull()?.toDouble() ?: Double.NaN
            if (!onConfirm(amount, updatePrices)) invalid = true
        }
    }
}

/** Case à cocher avec son libellé (.check-row). */
@Composable
private fun OrderCheckRow(label: String, checked: Boolean, onToggle: () -> Unit) {
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
