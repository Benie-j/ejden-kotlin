package com.ejden.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/** Équivalent de linear-gradient(<angle>deg, ...) en CSS. */
fun cssLinear(size: Size, angleDeg: Float, vararg stops: Pair<Float, Color>): Brush {
    val rad = Math.toRadians(angleDeg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = (-cos(rad)).toFloat()
    val length = abs(size.width * dx) + abs(size.height * dy)
    val cx = size.width / 2f
    val cy = size.height / 2f
    return Brush.linearGradient(
        *stops,
        start = Offset(cx - dx * length / 2f, cy - dy * length / 2f),
        end = Offset(cx + dx * length / 2f, cy + dy * length / 2f)
    )
}

/** Équivalent de radial-gradient(circle at X% Y%, couleur, transparent stop%) en CSS. */
fun cssRadial(size: Size, cx: Float, cy: Float, color: Color, stop: Float): Brush {
    val center = Offset(size.width * cx, size.height * cy)
    val fx = max(center.x, size.width - center.x)
    val fy = max(center.y, size.height - center.y)
    return Brush.radialGradient(
        0f to color,
        stop to color.copy(alpha = 0f),
        center = center,
        radius = hypot(fx, fy)
    )
}
