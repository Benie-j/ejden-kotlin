package com.ejden.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Bordure en pointillés (le « border: 1px dashed » du CSS). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier = drawBehind {
    val w = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(w / 2, w / 2),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = w,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
        )
    )
}
