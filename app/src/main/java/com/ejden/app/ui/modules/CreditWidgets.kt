package com.ejden.app.ui.modules

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.CREDIT_PAYMENT_METHODS
import com.ejden.app.data.CreditPayment
import com.ejden.app.data.PaymentMethod
import com.ejden.app.data.formatMoney
import com.ejden.app.data.formatNumber
import com.ejden.app.data.paymentLabel
import com.ejden.app.data.plainNumber
import com.ejden.app.data.shortDateText
import com.ejden.app.data.timeText
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import java.time.ZoneId

private fun Modifier.ring(visible: Boolean, color: Color, radius: Dp): Modifier = drawBehind {
    if (visible) {
        val width = 3.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(-width / 2, -width / 2),
            size = Size(size.width + width, size.height + width),
            cornerRadius = CornerRadius(radius.toPx() + width / 2),
            style = Stroke(width)
        )
    }
}

/** Liste lecture seule des paiements reçus (fiche client). */
@Composable
fun PaymentsList(payments: List<CreditPayment>, zone: ZoneId) {
    val c = Ejden.colors
    Column {
        payments.forEachIndexed { index, payment ->
            val cancelled = payment.cancelledAt != null
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
            if (index < payments.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        }
    }
}

/**
 * « Encaisser un paiement » : montant, mode, aperçu du reste.
 * onPay renvoie true si le paiement est enregistré.
 */
@Composable
fun PaymentForm(due: Double, onPay: (Int, PaymentMethod) -> Boolean, onError: (String) -> Unit) {
    val c = Ejden.colors
    var open by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(CREDIT_PAYMENT_METHODS.first()) }
    var menuOpen by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(Modifier.padding(top = 12.dp)) {
        if (!open) {
            ModuleActionButton("Encaisser un paiement", ModuleButtonStyle.Primary) { open = true }
        } else {
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
                            .ring(focused, c.primary.copy(alpha = 0.12f), 12.dp)
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
                            .tap { amountText = plainNumber(due) }
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
                    ModuleActionButton("Enregistrer le paiement", ModuleButtonStyle.Primary) {
                        if (saving) return@ModuleActionButton
                        val value = amountText.toLongOrNull()
                        if (value == null || value <= 0) {
                            onError("Saisissez un montant entier, en FCFA.")
                        } else {
                            saving = true
                            if (!onPay(value.toInt(), method)) saving = false
                        }
                    }
                    ModuleActionButton("Annuler", ModuleButtonStyle.Normal) {
                        open = false
                        amountText = ""
                        method = CREDIT_PAYMENT_METHODS.first()
                    }
                }
            }
        }
    }
}
