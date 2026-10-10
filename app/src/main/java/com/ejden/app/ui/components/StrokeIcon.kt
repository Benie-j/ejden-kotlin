package com.ejden.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

class IconCircle(val cx: Float, val cy: Float, val r: Float)

/** Icône SVG 24x24 : tracés (attribut d) + cercles, dessinés en traits arrondis. */
class IconSpec(val paths: List<String>, val circles: List<IconCircle> = emptyList())

object AppIcons {
    val Bell = IconSpec(
        listOf("M6 9a6 6 0 0 1 12 0c0 6 2.5 7.5 2.5 7.5h-17S6 15 6 9z", "M10 20a2 2 0 0 0 4 0")
    )
    val Home = IconSpec(listOf("M3 10.5L12 3l9 7.5", "M5.5 9.5V21h13V9.5", "M9.5 21v-6h5v6"))
    val Sales = IconSpec(listOf("M5 4h14v16H5z", "M8 8h8", "M8 12h8", "M8 16h5"))
    val Products = IconSpec(listOf("M4 7h16v13H4z", "M4 10h16", "M8 7V4h8v3", "M8 14h8"))
    val More = IconSpec(
        emptyList(),
        listOf(IconCircle(5f, 12f, 1.5f), IconCircle(12f, 12f, 1.5f), IconCircle(19f, 12f, 1.5f))
    )
    val Plus = IconSpec(listOf("M12 5v14", "M5 12h14"))
    val Close = IconSpec(listOf("M6 6L18 18", "M18 6L6 18"))
    val Arrow = IconSpec(listOf("M9 18l6-6-6-6"))
    val QuickSale = IconSpec(listOf("M5 5h14v14H5z", "M8 12h8", "M12 8v8"))
    val QuickProduct = IconSpec(listOf("M4 7h16v13H4z", "M8 7V4h8v3", "M8 12h8", "M12 9v6"))
    val QuickClient = IconSpec(
        listOf("M3 20c0-3.3 2.7-6 6-6s6 2.7 6 6", "M17 8h4", "M19 6v4"),
        listOf(IconCircle(9f, 8f, 3f))
    )
    val QuickExpense = IconSpec(
        listOf(
            "M4 7h16", "M7 4v3", "M17 4v3",
            "M6 7H18A2 2 0 0 1 20 9V18A2 2 0 0 1 18 20H6A2 2 0 0 1 4 18V9A2 2 0 0 1 6 7Z",
            "M8 13h8", "M12 10v6"
        )
    )
    val QuickCashIn = IconSpec(
        listOf(
            "M5 6H19A2 2 0 0 1 21 8V18A2 2 0 0 1 19 20H5A2 2 0 0 1 3 18V8A2 2 0 0 1 5 6Z",
            "M7 6V4h10v2", "M12 10v6", "M9 13h6"
        )
    )
}

@Composable
fun StrokeIcon(
    spec: IconSpec,
    color: Color,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 1.8f
) {
    val parsed = remember(spec) {
        spec.paths.map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(iconSize)) {
        val s = this.size.width / 24f
        withTransform({ scale(s, s, Offset.Zero) }) {
            val stroke = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (path in parsed) drawPath(path, color, style = stroke)
            for (circle in spec.circles) {
                drawCircle(color, circle.r, Offset(circle.cx, circle.cy), style = stroke)
            }
        }
    }
}

/** Clic sans ondulation ni effet de survol (comme les boutons web de l'app). */
fun Modifier.tap(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
