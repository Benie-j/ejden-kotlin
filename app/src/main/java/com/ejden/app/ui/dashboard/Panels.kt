package com.ejden.app.ui.dashboard

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ejden.app.data.Change
import com.ejden.app.data.ChangeTone
import com.ejden.app.data.DashboardData
import com.ejden.app.data.ResolvedPeriod
import com.ejden.app.data.StockSummary
import com.ejden.app.data.describeChange
import com.ejden.app.data.formatNumber
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.Radius

/** Carte blanche du tableau de bord : ombre légère, bordure fine, coins arrondis. */
@Composable
fun Modifier.dashCard(shape: Shape): Modifier {
    val c = Ejden.colors
    val glow = if (Ejden.dark) Color.Black.copy(alpha = 0.35f) else Color(0xFF172126).copy(alpha = 0.10f)
    return this
        .shadow(3.dp, shape, ambientColor = glow, spotColor = glow)
        .background(c.surface, shape)
        .border(1.dp, c.border, shape)
}

private fun Modifier.leftDivider(color: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
}

private fun Modifier.bottomDivider(color: Color): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawLine(color, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

@Composable
private fun toneColor(tone: ChangeTone): Color = when (tone) {
    ChangeTone.Up -> Ejden.colors.success
    ChangeTone.Down -> Ejden.colors.error
    ChangeTone.Neutral -> Ejden.colors.textLight
}

private fun trendText(change: Change, comparedTo: String): String =
    if (change.tone == ChangeTone.Neutral) change.text else "${change.text} vs $comparedTo"

// ---------------------------------------------------------------------------
// Indicateurs
// ---------------------------------------------------------------------------

@Composable
fun KpiGrid(data: DashboardData, period: ResolvedPeriod) {
    val summary = data.summary
    val revenueChange = describeChange(summary.revenue, data.previous.revenue)
    val salesChange = describeChange(summary.salesCount.toDouble(), data.previous.salesCount.toDouble())
    val dash = Change("\u2014", ChangeTone.Neutral)

    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KpiCard(
                "Chiffre d'affaires", period.label,
                if (summary.salesCount > 0) formatNumber(summary.revenue) else "\u2014",
                "FCFA",
                trendText(revenueChange, period.comparedTo), revenueChange.tone,
                Modifier.weight(1f).fillMaxHeight()
            )
            KpiCard(
                "Ventes", period.label,
                summary.salesCount.toString(),
                if (summary.salesCount > 1) "transactions" else "transaction",
                trendText(salesChange, period.comparedTo), salesChange.tone,
                Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            KpiCard(
                "Marge estimée", period.label,
                summary.margin?.let { formatNumber(it) } ?: "\u2014",
                "FCFA", dash.text, dash.tone,
                Modifier.weight(1f).fillMaxHeight()
            )
            KpiCard(
                "Trésorerie", "Actuelle",
                formatNumber(data.cashBalance),
                "FCFA", dash.text, dash.tone,
                Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    periodLabel: String,
    value: String,
    unit: String,
    trend: String,
    tone: ChangeTone,
    modifier: Modifier
) {
    val c = Ejden.colors
    val compact = LocalConfiguration.current.screenWidthDp <= 430
    val valueSize = if (compact) 20.sp else 22.sp

    Column(
        modifier
            .heightIn(min = if (compact) 118.dp else 125.dp)
            .dashCard(Radius.md)
            .padding(if (compact) 12.dp else 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                modifier = Modifier.weight(1f, fill = false),
                color = c.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight(600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(10.dp))
            Text(periodLabel, color = c.textLight, fontSize = 9.sp, maxLines = 1)
        }
        Spacer(Modifier.height(15.dp))
        Text(
            value,
            color = c.text,
            fontSize = valueSize,
            lineHeight = valueSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.03).em,
            maxLines = 1
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(unit, color = c.textLight, fontSize = 10.sp)
            Spacer(Modifier.width(6.dp))
            Text(
                trend,
                modifier = Modifier.weight(1f, fill = false),
                color = toneColor(tone),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Panneaux
// ---------------------------------------------------------------------------

@Composable
private fun PanelHeader(
    label: String,
    title: String,
    link: String?,
    onLink: () -> Unit,
    labelColor: Color = Ejden.colors.primary
) {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                color = labelColor,
                fontSize = 9.sp,
                fontWeight = FontWeight(800),
                letterSpacing = 0.11.em
            )
            Spacer(Modifier.height(3.dp))
            Text(title, color = c.text, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
        }
        if (link != null) {
            Text(
                link,
                modifier = Modifier.padding(start = 15.dp).tap(onLink),
                color = c.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CashPanel(data: DashboardData, period: ResolvedPeriod, onOpen: () -> Unit) {
    val c = Ejden.colors

    Column(Modifier.fillMaxWidth().dashCard(Radius.lg).padding(15.dp)) {
        PanelHeader("TRÉSORERIE", "Caisse", "Voir", onOpen)
        Spacer(Modifier.height(18.dp))

        Column(Modifier.fillMaxWidth().bottomDivider(c.border).padding(top = 15.dp, bottom = 19.dp)) {
            Text("Solde actuel", color = c.textSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(7.dp))
            Row {
                Text(
                    formatNumber(data.cashBalance),
                    modifier = Modifier.alignByBaseline(),
                    color = c.text,
                    fontSize = 31.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "FCFA",
                    modifier = Modifier.alignByBaseline(),
                    color = c.textSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f).padding(top = 16.dp)) {
                Text("Entrées · ${period.label}", color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(formatNumber(data.cashFlow.cashIn), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).leftDivider(c.border).padding(start = 16.dp, top = 16.dp)) {
                Text("Sorties · ${period.label}", color = c.textSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(formatNumber(data.cashFlow.cashOut), color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StockPanel(stock: StockSummary, onOpen: () -> Unit) {
    val c = Ejden.colors

    Column(Modifier.fillMaxWidth().dashCard(Radius.lg).padding(15.dp)) {
        PanelHeader("INVENTAIRE", "Stock", "Gérer", onOpen)
        Spacer(Modifier.height(18.dp))

        Row(Modifier.fillMaxWidth().bottomDivider(c.border)) {
            StockStat("Produits", stock.productsCount, c.text, Modifier.weight(1f))
            StockStat("Stock faible", stock.lowCount, c.warning, Modifier.weight(1f).leftDivider(c.border))
            StockStat("Ruptures", stock.outCount, c.error, Modifier.weight(1f).leftDivider(c.border))
        }

        if (stock.watchList.isEmpty()) {
            Text(
                if (stock.productsCount == 0) "Aucun produit enregistré." else "Aucun produit à surveiller.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 25.dp),
                color = c.textSecondary,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        } else {
            for (product in stock.watchList) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        product.name,
                        modifier = Modifier.weight(1f),
                        color = c.text,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(10.dp))
                    if (product.stock <= 0) {
                        Text("Rupture", color = c.error, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            "${product.stock} restant" + if (product.stock > 1) "s" else "",
                            color = c.warning,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockStat(label: String, value: Int, valueColor: Color, modifier: Modifier) {
    val c = Ejden.colors
    Column(
        modifier.padding(horizontal = 8.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = c.textSecondary, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value.toString(), color = valueColor, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MiniPanel(
    label: String,
    title: String,
    value: String,
    description: String,
    link: String,
    onLink: () -> Unit,
    labelColor: Color = Ejden.colors.primary
) {
    val c = Ejden.colors

    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .dashCard(Radius.md)
            .padding(18.dp)
    ) {
        Text(label, color = labelColor, fontSize = 9.sp, fontWeight = FontWeight(800), letterSpacing = 0.11.em)
        Spacer(Modifier.height(3.dp))
        Text(title, color = c.text, fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(15.dp))
        Text(value, color = c.text, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
        Text(description, color = c.textSecondary, fontSize = 9.sp)
        Spacer(Modifier.height(10.dp))
        Text(link, modifier = Modifier.tap(onLink), color = c.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
