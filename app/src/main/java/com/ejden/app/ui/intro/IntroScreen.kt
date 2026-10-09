package com.ejden.app.ui.intro

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.cssLinear
import com.ejden.app.ui.cssRadial
import kotlinx.coroutines.delay

/** Couleurs de index.css : elles suivent le thème de l'APPAREIL (prefers-color-scheme). */
class IntroPalette(val dark: Boolean) {
    val primary = Color(0xFF176B87)
    val primaryDark = Color(0xFF12566C)
    val text = if (dark) Color(0xFFF1F6F8) else Color(0xFF172126)
    val textSecondary = if (dark) Color(0xFF9BAAB0) else Color(0xFF68777D)
    val divider = (if (dark) Color(0xFF26373D) else Color(0xFFE3EAED)).copy(alpha = 0.8f)
    val gradA = if (dark) Color(0xFF111C20) else Color(0xFFF9FCFD)
    val gradB = if (dark) Color(0xFF0F181C) else Color(0xFFF6F9FA)
    val gradC = if (dark) Color(0xFF10242B) else Color(0xFFEEF6F8)
    val splashGlow = primary.copy(alpha = if (dark) 0.18f else 0.08f)
    val welcomeGlow1 = primary.copy(alpha = if (dark) 0.20f else 0.10f)
    val welcomeGlow2 = primary.copy(alpha = if (dark) 0.14f else 0.08f)
}

private val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

@Composable
fun IntroScreen(onStart: () -> Unit) {
    val palette = remember(isSystemInDarkTheme()) { IntroPalette(false) }.let {
        IntroPalette(isSystemInDarkTheme())
    }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5000)
        revealed = true
    }

    val splashAlpha by animateFloatAsState(
        if (revealed) 0f else 1f, tween(800, easing = EASE), label = "splashAlpha"
    )
    val welcomeAlpha by animateFloatAsState(
        if (revealed) 1f else 0f, tween(800, easing = EASE), label = "welcomeAlpha"
    )
    val welcomeShift by animateFloatAsState(
        if (revealed) 0f else 12f, tween(800, easing = EASE), label = "welcomeShift"
    )

    Box(Modifier.fillMaxSize()) {
        WelcomeLayer(
            palette = palette,
            onStart = onStart,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = welcomeAlpha
                    translationY = welcomeShift * density
                }
        )
        if (splashAlpha > 0f) {
            SplashLayer(
                palette = palette,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = splashAlpha }
            )
        }
    }
}

@Composable
private fun SplashLayer(palette: IntroPalette, modifier: Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(1400, easing = EASE)) }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(1400)
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            // Le splash bloque les appuis tant qu'il est visible.
            .pointerInput(Unit) { detectTapGestures { } }
            .drawBehind {
                drawRect(
                    cssLinear(
                        size, 145f,
                        0f to palette.gradA, 0.5f to palette.gradB, 1f to palette.gradC
                    )
                )
                drawRect(cssRadial(size, 0.5f, 0.5f, palette.splashGlow, 0.38f))
            },
        contentAlignment = Alignment.Center
    ) {
        EjdenLogo(
            modifier = Modifier
                .size(118.dp)
                .graphicsLayer {
                    val s = (0.82f + 0.18f * appear.value) * pulse
                    scaleX = s
                    scaleY = s
                    alpha = appear.value
                },
            color = palette.primary,
            animated = true
        )
    }
}

@Composable
private fun WelcomeLayer(palette: IntroPalette, onStart: () -> Unit, modifier: Modifier) {
    val config = LocalConfiguration.current
    val short = config.screenHeightDp <= 700
    val h1Size = (config.screenWidthDp * 0.08f).coerceIn(30f, 42f)

    Box(
        modifier = modifier
            .drawBehind {
                drawRect(
                    cssLinear(
                        size, 145f,
                        0f to palette.gradA, 0.48f to palette.gradB, 1f to palette.gradC
                    )
                )
                drawRect(cssRadial(size, 0.88f, 0.78f, palette.welcomeGlow2, 0.30f))
                drawRect(cssRadial(size, 0.15f, 0.20f, palette.welcomeGlow1, 0.32f))
            }
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.widthIn(max = 430.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EjdenLogo(
                modifier = Modifier.size(92.dp),
                color = palette.primary,
                animated = false
            )
            Spacer(Modifier.height(14.dp))

            Text(
                "EJDEN",
                color = palette.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(750),
                letterSpacing = 0.28.em
            )
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .width(26.dp)
                    .height(2.dp)
                    .background(palette.primary, RoundedCornerShape(50))
            )
            Spacer(Modifier.height(if (short) 30.dp else 46.dp))

            Text(
                "VOTRE ESPACE DE GESTION",
                color = palette.primary,
                fontSize = 11.sp,
                fontWeight = FontWeight(750),
                letterSpacing = 0.14.em
            )
            Spacer(Modifier.height(13.dp))
            Text(
                "Votre espace EJDEN est prêt.",
                modifier = Modifier.widthIn(max = 380.dp),
                color = palette.text,
                fontSize = h1Size.sp,
                lineHeight = (h1Size * 1.08f).sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-0.035).em,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(17.dp))
            Text(
                "Commencez à enregistrer vos produits, vos ventes et vos clients.",
                modifier = Modifier.widthIn(max = 330.dp),
                color = palette.textSecondary,
                fontSize = 15.sp,
                lineHeight = (15 * 1.65f).sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(if (short) 28.dp else 38.dp))
            FeaturesRow(palette)
            Spacer(Modifier.height(if (short) 22.dp else 30.dp))
            StartButton(palette, onStart)
        }
    }
}

@Composable
private fun FeaturesRow(palette: IntroPalette) {
    val items = listOf("01" to "Produits", "02" to "Ventes", "03" to "Clients")

    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 1.dp.toPx()
                drawLine(palette.divider, Offset(0f, w / 2), Offset(size.width, w / 2), w)
                drawLine(
                    palette.divider,
                    Offset(0f, size.height - w / 2),
                    Offset(size.width, size.height - w / 2),
                    w
                )
            }
    ) {
        items.forEachIndexed { index, (number, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 58.dp)
                    .drawBehind {
                        if (index > 0) {
                            val w = 1.dp.toPx()
                            drawLine(palette.divider, Offset(w / 2, 0f), Offset(w / 2, size.height), w)
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
            ) {
                Text(
                    number,
                    color = palette.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = 0.12.em
                )
                Text(label, color = palette.text, fontSize = 12.sp, fontWeight = FontWeight(650))
            }
        }
    }
}

@Composable
private fun StartButton(palette: IntroPalette, onStart: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.985f else 1f, tween(180, easing = EASE), label = "buttonScale"
    )
    val background by animateColorAsState(
        if (pressed) palette.primaryDark else palette.primary,
        tween(180, easing = EASE),
        label = "buttonColor"
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = palette.primary.copy(alpha = 0.18f),
                spotColor = palette.primary.copy(alpha = 0.18f)
            )
            .background(background, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onStart)
            .heightIn(min = 58.dp)
            .padding(start = 22.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "Accéder à mon espace",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            Modifier
                .size(34.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("→", color = Color.White, fontSize = 19.sp)
        }
    }
}
