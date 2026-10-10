package com.ejden.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.Radius

/** Durée d'animation : quasi nulle si « Réduire les animations » est activé. */
private fun dur(base: Int, reduce: Boolean): Int = if (reduce) 1 else base

private class QuickAction(
    val id: String,
    val icon: IconSpec,
    val title: String,
    val subtitle: String
)

private val QUICK_ACTIONS = listOf(
    QuickAction("ventes", AppIcons.QuickSale, "Nouvelle vente", "Enregistrer une nouvelle vente"),
    QuickAction("ajouter-produit", AppIcons.QuickProduct, "Ajouter un produit", "Enregistrer un nouveau produit"),
    QuickAction("clients", AppIcons.QuickClient, "Ajouter un client", "Créer une nouvelle fiche client"),
    QuickAction("depenses", AppIcons.QuickExpense, "Nouvelle dépense", "Enregistrer une dépense"),
    QuickAction("caisse", AppIcons.QuickCashIn, "Entrée de caisse", "Enregistrer une entrée d'argent")
)

// ---------------------------------------------------------------------------
// Panneau « Action rapide »
// ---------------------------------------------------------------------------

@Composable
fun QuickActionsSheet(visible: Boolean, onClose: () -> Unit, onSelect: (String) -> Unit) {
    val c = Ejden.colors
    val reduce = Ejden.reduceMotion
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrim = if (Ejden.dark) Color.Black.copy(alpha = 0.60f) else Color(0xFF0A1216).copy(alpha = 0.45f)
    val shadowColor = Color.Black.copy(alpha = if (Ejden.dark) 0.45f else 0.18f)
    val offsetPx = with(LocalDensity.current) { 25.dp.roundToPx() }
    val shape = RoundedCornerShape(24.dp)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(dur(220, reduce))),
            exit = fadeOut(tween(dur(220, reduce)))
        ) {
            Box(Modifier.fillMaxSize().background(scrim).tap(onClose))
        }

        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(dur(220, reduce))) +
                slideInVertically(tween(dur(250, reduce))) { offsetPx },
            exit = fadeOut(tween(dur(220, reduce))) +
                slideOutVertically(tween(dur(250, reduce))) { offsetPx }
        ) {
            Column(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + navInset)
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .shadow(24.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
                    .background(c.surface, shape)
                    .border(1.dp, c.border, shape)
                    .tap { }
                    .padding(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ACTION RAPIDE",
                            color = c.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight(800),
                            letterSpacing = 0.12.em
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Que souhaitez-vous faire ?",
                            color = c.text,
                            fontSize = 20.sp,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(c.background, RoundedCornerShape(12.dp))
                            .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .tap(onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        StrokeIcon(AppIcons.Close, c.textSecondary, 18.dp, strokeWidth = 2f)
                    }
                }

                Spacer(Modifier.height(18.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (action in QUICK_ACTIONS) {
                        QuickActionRow(action) { onSelect(action.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionRow(action: QuickAction, onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(180), label = "quickScale")
    val shape = RoundedCornerShape(16.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(if (pressed) c.primarySoft else c.background, shape)
            .border(1.dp, if (pressed) c.primary else c.border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(46.dp).background(c.primarySoft, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            StrokeIcon(action.icon, c.primary, 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(action.title, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(action.subtitle, color = c.textSecondary, fontSize = 11.sp)
        }
        StrokeIcon(AppIcons.Arrow, c.textSecondary, 18.dp)
    }
}

// ---------------------------------------------------------------------------
// Menu « Plus » : tous les modules par catégorie
// ---------------------------------------------------------------------------

@Composable
fun MoreMenuSheet(
    visible: Boolean,
    onClose: () -> Unit,
    onOpen: (String) -> Unit,
    onSoon: (String) -> Unit
) {
    val c = Ejden.colors
    val reduce = Ejden.reduceMotion
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scrim = Color(0xFF0A1216).copy(alpha = 0.45f)
    val offsetPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible,
            enter = fadeIn(tween(dur(220, reduce))),
            exit = fadeOut(tween(dur(220, reduce)))
        ) {
            Box(Modifier.fillMaxSize().background(scrim).tap(onClose))
        }

        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(dur(220, reduce))) +
                slideInVertically(tween(dur(220, reduce))) { offsetPx },
            exit = fadeOut(tween(dur(220, reduce))) +
                slideOutVertically(tween(dur(220, reduce))) { offsetPx }
        ) {
            Column(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .shadow(
                        16.dp, shape,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.18f)
                    )
                    .background(c.surface, shape)
                    .tap { }
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Tous les modules",
                        color = c.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(c.surface, CircleShape)
                            .border(1.dp, c.border, CircleShape)
                            .tap(onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", color = c.text, fontSize = 14.sp)
                    }
                }

                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 18.dp, end = 18.dp, bottom = 24.dp + navInset),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    for (category in MODULE_CATEGORIES) {
                        Column {
                            Text(
                                category.label.uppercase(),
                                color = c.textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.08.em
                            )
                            Spacer(Modifier.height(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (pair in category.modules.chunked(2)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        for (module in pair) {
                                            MoreTile(
                                                module,
                                                Modifier.weight(1f)
                                            ) {
                                                if (module.soon) onSoon(module.label) else onOpen(module.id)
                                            }
                                        }
                                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreTile(module: AppModule, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = Radius.md

    Box(
        modifier
            .alpha(if (module.soon) 0.6f else 1f)
            .background(if (pressed) c.primarySoft else c.surface, shape)
            .border(1.dp, c.border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier.size(34.dp).background(c.primarySoft, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    module.label.take(1).uppercase(),
                    color = c.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight(800)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    module.label,
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    module.description,
                    color = c.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (module.soon) {
            Text(
                "Bientôt",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 6.dp)
                    .background(c.warningSoft, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = c.warning,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Message temporaire (« X : bientôt disponible. »)
// ---------------------------------------------------------------------------

@Composable
fun AppToast(text: String, visible: Boolean, modifier: Modifier = Modifier) {
    val c = Ejden.colors
    val reduce = Ejden.reduceMotion
    val offsetPx = with(LocalDensity.current) { 12.dp.roundToPx() }

    AnimatedVisibility(
        visible,
        modifier = modifier,
        enter = fadeIn(tween(dur(200, reduce))) +
            slideInVertically(tween(dur(200, reduce))) { offsetPx },
        exit = fadeOut(tween(dur(200, reduce))) +
            slideOutVertically(tween(dur(200, reduce))) { offsetPx }
    ) {
        Text(
            text,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .shadow(8.dp, Radius.md)
                .background(c.text, Radius.md)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            color = c.surface,
            fontSize = 12.sp,
            fontWeight = FontWeight(600),
            textAlign = TextAlign.Center
        )
    }
}
