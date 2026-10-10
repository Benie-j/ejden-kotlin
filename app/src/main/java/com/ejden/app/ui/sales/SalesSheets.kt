package com.ejden.app.ui.sales

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.CartLine
import com.ejden.app.data.Client
import com.ejden.app.data.NEW_CLIENT
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.Product
import com.ejden.app.data.calcPayment
import com.ejden.app.data.formatDebt
import com.ejden.app.data.formatMoney
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.ProductIcons
import com.ejden.app.ui.components.SalesIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.dashedBorder
import com.ejden.app.ui.components.pressable
import com.ejden.app.ui.components.rememberPhoto
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden

private fun dur(base: Int, reduce: Boolean): Int = if (reduce) 1 else base

/** Photo d'un produit (ou icône « boîte »), carrée et arrondie. */
@Composable
fun SalesPhoto(product: Product, size: Dp, radius: Dp, targetPx: Int, iconSize: Dp) {
    val c = Ejden.colors
    val bitmap = rememberPhoto(product.photo, targetPx)

    Box(
        Modifier.size(size).clip(RoundedCornerShape(radius)).background(c.primarySoft),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = product.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            StrokeIcon(SalesIcons.Box, c.primary, iconSize)
        }
    }
}

/** Feuille qui glisse depuis le bas, avec voile (panier et encaissement). */
@Composable
fun SalesSheet(
    visible: Boolean,
    scrim: Color,
    heightDp: Float,
    maxDp: Float,
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = Ejden.colors
    val reduce = Ejden.reduceMotion
    val easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val panelHeight = minOf(screenHeight * heightDp, maxDp).dp

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(dur(250, reduce))),
            exit = fadeOut(tween(dur(250, reduce)))
        ) {
            Box(Modifier.fillMaxSize().background(scrim).tap(onClose))
        }

        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(dur(280, reduce), easing = easing)) { it },
            exit = slideOutVertically(tween(dur(280, reduce), easing = easing)) { it }
        ) {
            Column(
                Modifier
                    .imePadding()
                    .fillMaxWidth()
                    .height(panelHeight)
                    .shadow(
                        16.dp, shape,
                        ambientColor = Color(0xFF172126).copy(alpha = 0.30f),
                        spotColor = Color(0xFF172126).copy(alpha = 0.30f)
                    )
                    .background(c.surface, shape)
                    .tap { },
                content = content
            )
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val c = Ejden.colors
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .pressable(0.94f, onBack)
                    .background(c.surface, RoundedCornerShape(12.dp))
                    .border(1.dp, c.border, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                StrokeIcon(ProductIcons.Back, c.text, 21.dp)
            }
            Column {
                Text(title, color = c.text, fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight(750))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = c.textSecondary, fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun fieldBackground(): Color = if (Ejden.dark) Color(0xFF202D32) else Ejden.colors.background

private fun pluralize(count: Int, word: String): String = "$count $word" + if (count > 1) "s" else ""

// ---------------------------------------------------------------------------
// Panier
// ---------------------------------------------------------------------------

@Composable
fun CartPanel(
    visible: Boolean,
    lines: List<CartLine>,
    quantity: Int,
    subtotal: Double,
    discount: Double,
    total: Double,
    onClose: () -> Unit,
    onChange: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onDiscount: () -> Unit,
    onCheckout: () -> Unit
) {
    val c = Ejden.colors
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SalesSheet(visible, Color(0xFF172126).copy(alpha = 0.46f), 0.92f, 760f, onClose) {
        SheetHeader("Panier", pluralize(quantity, "article"), onClose)

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (lines.isEmpty()) {
                EmptyCard(
                    SalesIcons.Cart,
                    "Votre panier est vide",
                    "Ajoutez des produits pour commencer la vente."
                ) {}
            } else {
                for (line in lines) CartItem(line, onChange, onRemove)
            }
        }

        // Informations de vente.
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border).offset(y = (-12).dp))
            Text("Client", color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight(650))
            Spacer(Modifier.height(7.dp))
            // Dans l'original, cette liste ne propose que « Client comptoir ».
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(fieldBackground(), RoundedCornerShape(11.dp))
                    .border(1.dp, c.border, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Client comptoir", color = c.text, fontSize = 13.sp)
                StrokeIcon(SalesIcons.ChevronDown, c.textSecondary, 16.dp)
            }
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 42.dp)
                    .border(1.dp, c.border, RoundedCornerShape(11.dp))
                    .tap(onDiscount)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Remise", color = c.text, fontSize = 12.sp)
                Text(formatMoney(discount), color = c.primary, fontSize = 12.sp, fontWeight = FontWeight(750))
            }
        }

        // Résumé.
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
            SummaryLine("Sous-total", formatMoney(subtotal))
            SummaryLine("Remise", formatMoney(discount))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Total", color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(formatMoney(total), color = c.primary, fontSize = 19.sp, fontWeight = FontWeight(800))
            }
        }

        // Encaisser.
        ActionButton(
            label = "Encaisser",
            amount = formatMoney(total),
            enabled = lines.isNotEmpty(),
            onClick = onCheckout,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp + navInset)
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = c.textSecondary, fontSize = 12.sp)
        Text(value, color = c.text, fontSize = 12.sp, fontWeight = FontWeight(650))
    }
}

@Composable
private fun CartItem(line: CartLine, onChange: (String, Int) -> Unit, onRemove: (String) -> Unit) {
    val c = Ejden.colors
    val product = line.product

    Column {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SalesPhoto(product, 48.dp, 11.dp, 160, 22.dp)

            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(formatMoney(product.salePrice) + " / unité", color = c.textSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatMoney(product.salePrice * line.quantity),
                    color = c.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight(750)
                )
                Row(
                    Modifier.padding(top = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    QuantityButton(SalesIcons.Minus) { onChange(product.id, -1) }
                    Box(Modifier.defaultMinSize(minWidth = 25.dp), contentAlignment = Alignment.Center) {
                        Text(
                            line.quantity.toString(),
                            color = c.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight(750)
                        )
                    }
                    QuantityButton(AppIcons.Plus) { onChange(product.id, 1) }
                }
            }

            Box(
                Modifier
                    .size(34.dp)
                    .pressable(0.92f) { onRemove(product.id) }
                    .background(c.error.copy(alpha = if (Ejden.dark) 0.14f else 0.08f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                StrokeIcon(SalesIcons.Trash, c.error, 17.dp, strokeWidth = 1.7f)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
private fun QuantityButton(icon: com.ejden.app.ui.components.IconSpec, onClick: () -> Unit) {
    val c = Ejden.colors
    Box(
        Modifier
            .size(30.dp)
            .pressable(0.92f, onClick)
            .background(fieldBackground(), RoundedCornerShape(9.dp))
            .border(1.dp, c.border, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        StrokeIcon(icon, c.text, 14.dp)
    }
}

/** Bouton large « libellé  ·  montant » (Encaisser, Valider l'encaissement). */
@Composable
fun ActionButton(
    label: String,
    amount: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(14.dp)
    val glow = c.primary.copy(alpha = 0.20f)

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pressable(0.985f, onClick)
                        .shadow(10.dp, shape, ambientColor = glow, spotColor = glow)
                        .background(c.primary, shape)
                } else {
                    Modifier.background(c.border, shape)
                }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val color = if (enabled) Color.White else c.textSecondary
        Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight(750))
        Text(amount, color = color, fontSize = 14.sp, fontWeight = FontWeight(800))
    }
}

@Composable
fun EmptyCard(
    icon: com.ejden.app.ui.components.IconSpec,
    title: String,
    text: String,
    action: @Composable () -> Unit
) {
    val c = Ejden.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.surface, RoundedCornerShape(18.dp))
            .dashedBorder(c.border, 18.dp)
            .padding(horizontal = 22.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(52.dp).background(c.primarySoft, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(icon, c.primary, 25.dp, strokeWidth = 1.7f)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            text,
            modifier = Modifier.widthIn(max = 260.dp),
            color = c.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        action()
    }
}

// ---------------------------------------------------------------------------
// Encaissement
// ---------------------------------------------------------------------------

private val METHODS = listOf(
    PaymentMethod.Cash to "Espèces",
    PaymentMethod.MobileMoney to "Mobile Money",
    PaymentMethod.Card to "Carte",
    PaymentMethod.Other to "Autre",
    PaymentMethod.Credit to "Crédit"
)

@Composable
fun CheckoutPanel(
    visible: Boolean,
    total: Double,
    method: PaymentMethod,
    allowCredit: Boolean,
    amountField: TextFieldValue,
    onAmountChange: (TextFieldValue) -> Unit,
    clients: List<Client>,
    debts: Map<String, Double>,
    clientChoice: String,
    onClientChoice: (String) -> Unit,
    typedName: String,
    onTypedName: (String) -> Unit,
    saveClient: Boolean,
    onSaveClient: (Boolean) -> Unit,
    onMethod: (PaymentMethod) -> Unit,
    onClose: () -> Unit,
    onConfirm: () -> Unit,
    nameFocus: FocusRequester,
    amountFocus: FocusRequester
) {
    val c = Ejden.colors
    val dark = Ejden.dark
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val calc = calcPayment(total, amountField.text, method)
    val isCredit = method == PaymentMethod.Credit

    SalesSheet(
        visible,
        if (dark) Color.Black.copy(alpha = 0.58f) else Color(0xFF172126).copy(alpha = 0.46f),
        0.88f, 720f, onClose
    ) {
        SheetHeader("Encaissement", "Paiement de la vente", onClose)

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 20.dp + navInset)
        ) {
            // Total à payer.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 76.dp)
                    .background(c.primarySoft, RoundedCornerShape(16.dp))
                    .border(
                        1.dp,
                        Color(0xFF176B87).copy(alpha = if (dark) 0.25f else 0.12f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Total à payer", color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight(600))
                Text(
                    formatMoney(total),
                    color = c.primary,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight(800)
                )
            }

            // Moyens de paiement.
            Spacer(Modifier.height(20.dp))
            FieldLabel("Moyen de paiement")
            Spacer(Modifier.height(8.dp))
            val methods = METHODS.filter { allowCredit || it.first != PaymentMethod.Credit }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (pair in methods.chunked(2)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((value, label) in pair) {
                            MethodButton(label, value == method, Modifier.weight(1f)) { onMethod(value) }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Client d'une vente à crédit.
            if (isCredit) {
                Spacer(Modifier.height(20.dp))
                BorrowerSection(
                    clients, debts, clientChoice, onClientChoice,
                    typedName, onTypedName, saveClient, onSaveClient, nameFocus
                )
            }

            // Montant encaissé.
            Spacer(Modifier.height(20.dp))
            FieldLabel("Montant encaissé")
            Spacer(Modifier.height(8.dp))
            AmountInput(amountField, onAmountChange, amountFocus)

            // Reste et monnaie.
            Column(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .background(fieldBackground(), RoundedCornerShape(13.dp))
                    .border(1.dp, c.border, RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                BalanceLine("Reste à payer", formatMoney(calc.remaining), c.text)
                BalanceLine("Monnaie", formatMoney(calc.change), c.success)
            }

            // Validation.
            ActionButton(
                label = "Valider l'encaissement",
                amount = formatMoney(calc.paid),
                enabled = calc.canConfirm,
                onClick = onConfirm,
                modifier = Modifier.padding(top = 18.dp)
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Ejden.colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight(650))
}

@Composable
private fun BalanceLine(label: String, value: String, valueColor: Color) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 28.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Ejden.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight(550))
        Text(value, color = valueColor, fontSize = 13.sp, fontWeight = FontWeight(750))
    }
}

@Composable
private fun MethodButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 46.dp)
            .pressable(0.97f, onClick)
            .background(if (selected) c.primarySoft else fieldBackground(), shape)
            .border(1.dp, if (selected) c.primary else c.border, shape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) c.primary else c.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight(650)
        )
    }
}

@Composable
private fun AmountInput(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, focus: FocusRequester) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(c.surface, shape)
            .border(1.dp, if (focused) c.primary else c.border, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = { new ->
                val digits = new.text.filter { it.isDigit() }
                onChange(if (digits == new.text) new else new.copy(text = digits))
            },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focus)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(color = c.text, fontSize = 20.sp, fontWeight = FontWeight(750)),
            cursorBrush = SolidColor(c.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text("0", color = c.textSecondary.copy(alpha = 0.55f), fontSize = 20.sp, fontWeight = FontWeight(750))
                    }
                    inner()
                }
            }
        )
        Spacer(Modifier.width(8.dp))
        Text("F", color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BorrowerSection(
    clients: List<Client>,
    debts: Map<String, Double>,
    choice: String,
    onChoice: (String) -> Unit,
    typedName: String,
    onTypedName: (String) -> Unit,
    saveClient: Boolean,
    onSaveClient: (Boolean) -> Unit,
    nameFocus: FocusRequester
) {
    val c = Ejden.colors
    var expanded by remember { mutableStateOf(false) }

    fun label(id: String): String = when {
        id == "" -> "Choisir un client…"
        id == NEW_CLIENT -> "＋ Nouveau client"
        else -> {
            val client = clients.firstOrNull { it.id == id }
            val debt = debts[id] ?: 0.0
            when {
                client == null -> "Choisir un client…"
                debt > 0.0 -> client.name + " (doit " + formatDebt(debt) + ")"
                else -> client.name
            }
        }
    }

    val options = (if (clients.isNotEmpty()) listOf("") else emptyList()) + clients.map { it.id } + NEW_CLIENT
    val chosen = clients.firstOrNull { it.id == choice }
    val chosenDebt = if (chosen != null) (debts[chosen.id] ?: 0.0) else 0.0

    Column {
        FieldLabel("Client")
        Spacer(Modifier.height(8.dp))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(c.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .tap { expanded = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    label(choice),
                    modifier = Modifier.weight(1f),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(550),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                StrokeIcon(SalesIcons.ChevronDown, c.textSecondary, 18.dp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                for (id in options) {
                    DropdownMenuItem(
                        text = { Text(label(id)) },
                        onClick = {
                            expanded = false
                            onChoice(id)
                        }
                    )
                }
            }
        }

        if (chosen != null && chosenDebt > 0.0) {
            Text(
                chosen.name + " doit déjà " + formatDebt(chosenDebt) + ".",
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .background(c.primarySoft, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = c.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight(600)
            )
        }

        if (choice == NEW_CLIENT) {
            Spacer(Modifier.height(12.dp))
            FieldLabel("Nom du client")
            Spacer(Modifier.height(8.dp))

            var focused by remember { mutableStateOf(false) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(c.surface, RoundedCornerShape(14.dp))
                    .border(1.dp, if (focused) c.primary else c.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = typedName,
                    onValueChange = { onTypedName(it.take(80)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus)
                        .onFocusChanged { focused = it.isFocused },
                    singleLine = true,
                    textStyle = TextStyle(color = c.text, fontSize = 14.sp, fontWeight = FontWeight(550)),
                    cursorBrush = SolidColor(c.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (typedName.isEmpty()) {
                                Text(
                                    "Ex. Kouassi Jean",
                                    color = c.textSecondary.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight(550)
                                )
                            }
                            inner()
                        }
                    }
                )
            }

            Row(
                Modifier.padding(top = 10.dp).tap { onSaveClient(!saveClient) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(if (saveClient) c.primary else c.surface, RoundedCornerShape(4.dp))
                        .border(1.5.dp, if (saveClient) c.primary else c.textSecondary, RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (saveClient) StrokeIcon(SalesIcons.Check, Color.White, 14.dp, strokeWidth = 3f)
                }
                Text("Enregistrer ce client", color = c.text, fontSize = 13.sp, fontWeight = FontWeight(550))
            }
        }
    }
}
