package com.ejden.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.intro.EjdenLogo
import com.ejden.app.ui.theme.Ejden

@Composable
fun Topbar(
    brandName: String,
    greeting: String,
    initial: String,
    unread: Int,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    val c = Ejden.colors

    Column(Modifier.fillMaxWidth().background(c.surface).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(61.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EjdenLogo(Modifier.size(36.dp), c.primary, animated = false)
            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    brandName,
                    color = c.text,
                    fontSize = 17.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.08.em,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    greeting,
                    color = c.textLight,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(Modifier.size(40.dp).tap(onNotifications), contentAlignment = Alignment.Center) {
                StrokeIcon(AppIcons.Bell, c.textSecondary, 22.dp)
                if (unread > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp)
                            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                            .background(c.error, CircleShape)
                            .border(2.dp, c.surface, CircleShape)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (unread > 99) "99+" else unread.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            Box(
                Modifier.tap(onProfile).padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 8.dp)
            ) {
                Box(
                    Modifier.size(32.dp).background(c.primarySoft, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial, color = c.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
fun BottomNav(
    onHome: () -> Unit,
    onSales: () -> Unit,
    onAdd: () -> Unit,
    onProducts: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = Ejden.colors
    val dark = Ejden.dark
    val shape = RoundedCornerShape(22.dp)

    val background = if (dark) Color(0xFF172126).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.96f)
    val borderColor = if (dark) Color.White.copy(alpha = 0.08f) else c.border
    val shadowColor = if (dark) Color.Black.copy(alpha = 0.30f) else Color(0xFF172126).copy(alpha = 0.10f)

    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(14.dp, shape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(background, shape)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 7.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Accueil", AppIcons.Home, active = true, Modifier.weight(1f), onClick = onHome)
        NavItem("Ventes", AppIcons.Sales, active = false, Modifier.weight(1f), onClick = onSales)
        Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) { AddButton(onAdd) }
        NavItem("Produits", AppIcons.Products, active = false, Modifier.weight(1f), onClick = onProducts)
        NavItem("Plus", AppIcons.More, active = false, Modifier.weight(1f), iconSize = 22.dp, onClick = onMore)
    }
}

@Composable
private fun NavItem(
    label: String,
    icon: IconSpec,
    active: Boolean,
    modifier: Modifier,
    iconSize: Dp = 21.dp,
    onClick: () -> Unit
) {
    val c = Ejden.colors
    val color = if (active) c.primary else c.textSecondary
    val lift by animateDpAsState(if (active) (-1).dp else 0.dp, tween(200), label = "navLift")

    Box(modifier.height(58.dp).tap(onClick), contentAlignment = Alignment.Center) {
        if (active) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 3.dp)
                    .size(4.dp)
                    .background(c.primary, CircleShape)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            StrokeIcon(
                icon, color, iconSize,
                Modifier.offset(y = lift),
                strokeWidth = if (active) 2f else 1.8f
            )
            Text(
                label,
                color = color,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight(600),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AddButton(onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, tween(180), label = "addScale")
    val shape = RoundedCornerShape(18.dp)
    val glow = if (Ejden.dark) Color.Black.copy(alpha = 0.30f) else c.primary.copy(alpha = 0.28f)

    Box(
        Modifier
            .size(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(10.dp, shape, ambientColor = glow, spotColor = glow)
            .background(c.primary, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        StrokeIcon(AppIcons.Plus, Color.White, 25.dp, strokeWidth = 2f)
    }
}
