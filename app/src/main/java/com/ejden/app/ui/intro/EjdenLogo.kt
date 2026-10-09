package com.ejden.app.ui.intro

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlinx.coroutines.launch

private class LogoShape(val path: Path, val length: Float)

private fun shape(d: String): LogoShape {
    val path = PathParser().parsePathString(d).toPath()
    val measure = PathMeasure()
    measure.setPath(path, false)
    return LogoShape(path, measure.length)
}

// Tracés exacts du logo (viewBox 120 x 120).
private val LINES by lazy {
    listOf(
        shape("M34 38 C42 28 54 24 67 28 L84 38"),
        shape("M34 38 L34 63 C34 72 41 79 50 79"),
        shape("M50 79 L67 92 C72 96 80 95 84 90"),
        shape("M84 38 L84 65 C84 75 78 82 68 82 L50 82")
    )
}
private val CORE by lazy { shape("M49 58 L70 58") }

private val EASE = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val DRAW_EASE = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private fun DrawScope.drawPartial(
    shape: LogoShape,
    visible: Float,
    measure: PathMeasure,
    segment: Path,
    color: Color,
    stroke: Stroke
) {
    if (visible <= 0f) return
    if (visible >= shape.length) {
        drawPath(shape.path, color, style = stroke)
        return
    }
    measure.setPath(shape.path, false)
    segment.rewind()
    measure.getSegment(0f, visible, segment, true)
    drawPath(segment, color, style = stroke)
}

/** animated = true : le logo se dessine (splash) ; false : logo complet (accueil). */
@Composable
fun EjdenLogo(modifier: Modifier, color: Color, animated: Boolean) {
    val draw = remember { Animatable(if (animated) 0f else 1f) }
    val core = remember { Animatable(if (animated) 0f else 1f) }
    val dot = remember { Animatable(if (animated) 0f else 1f) }
    val halo = remember { Animatable(if (animated) 0f else 0.08f) }
    val measure = remember { PathMeasure() }
    val segment = remember { Path() }

    if (animated) {
        LaunchedEffect(Unit) {
            launch { draw.animateTo(1f, tween(1800, easing = DRAW_EASE)) }
            launch { core.animateTo(1f, tween(1000, delayMillis = 900, easing = EASE)) }
            launch { dot.animateTo(1f, tween(500, delayMillis = 1400, easing = EASE)) }
            launch { halo.animateTo(0.08f, tween(1200, delayMillis = 300, easing = EASE)) }
        }
    }

    Canvas(modifier) {
        val s = size.width / 120f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawCircle(
                color = color.copy(alpha = halo.value),
                radius = 48f,
                center = Offset(60f, 60f),
                style = Stroke(1.2f)
            )

            val lineStroke = Stroke(7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val lineVisible = 240f * draw.value
            for (line in LINES) {
                drawPartial(line, lineVisible, measure, segment, color, lineStroke)
            }

            drawPartial(
                CORE, 30f * core.value, measure, segment, color,
                Stroke(5f, cap = StrokeCap.Round)
            )

            drawCircle(
                color = color.copy(alpha = dot.value),
                radius = 4f,
                center = Offset(60f, 58f)
            )
        }
    }
}
