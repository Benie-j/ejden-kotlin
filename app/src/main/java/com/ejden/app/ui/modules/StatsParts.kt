package com.ejden.app.ui.modules

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.Trend
import com.ejden.app.data.statCompact
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.Radius

enum class StatTone { Neutral, Good, Warn, Bad }
enum class LineTone { None, Positive, Negative }

@Composable
fun StatNote(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 8.dp),
        color = Ejden.colors.textSecondary,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
}

@Composable
fun StatEmpty(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 6.dp),
        color = Ejden.colors.textSecondary,
        fontSize = 13.sp
    )
}

@Composable
private fun StatLineFrame(label: String, divider: Boolean, trailing: @Composable () -> Unit) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), color = c.textSecondary, fontSize = 14.sp)
            trailing()
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
fun StatLine(label: String, value: String, tone: LineTone = LineTone.None, divider: Boolean = true) {
    val c = Ejden.colors
    StatLineFrame(label, divider) {
        Text(
            value,
            color = when (tone) {
                LineTone.Positive -> c.success
                LineTone.Negative -> c.error
                LineTone.None -> c.text
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            softWrap = false
        )
    }
}

@Composable
fun StatTrendLine(label: String, trend: Trend, divider: Boolean = true) {
    StatLineFrame(label, divider) {
        when (trend) {
            Trend.Up -> StatBadge("Hausse", StatTone.Good)
            Trend.Down -> StatBadge("Baisse", StatTone.Bad)
            Trend.Flat -> StatBadge("Stable", StatTone.Neutral)
        }
    }
}

@Composable
fun StatBadge(text: String, tone: StatTone = StatTone.Neutral) {
    val c = Ejden.colors
    val (background, foreground) = when (tone) {
        StatTone.Neutral -> c.primarySoft to c.primary
        StatTone.Good -> c.successSoft to c.success
        StatTone.Warn -> c.warningSoft to c.warning
        StatTone.Bad -> c.errorSoft to c.error
    }
    Text(
        text,
        modifier = Modifier.background(background, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
        color = foreground,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold
    )
}

data class StatKpiData(val label: String, val value: String, val main: Boolean = false)

@Composable
fun StatKpis(items: List<StatKpiData>) {
    // Même logique que la grille CSS à 2 colonnes : un indicateur "main" occupe toute la ligne.
    val rows = ArrayList<List<StatKpiData>>()
    var pending: StatKpiData? = null
    for (kpi in items) {
        if (kpi.main) {
            pending?.let { rows.add(listOf(it)) }
            pending = null
            rows.add(listOf(kpi))
        } else if (pending == null) {
            pending = kpi
        } else {
            rows.add(listOf(pending, kpi))
            pending = null
        }
    }
    pending?.let { rows.add(listOf(it)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (row in rows) {
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (kpi in row) StatKpiCard(kpi, Modifier.weight(1f).fillMaxHeight())
                if (row.size == 1 && !row[0].main) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatKpiCard(kpi: StatKpiData, modifier: Modifier) {
    val c = Ejden.colors
    val background = if (kpi.main) c.primary else c.surface
    val border = if (kpi.main) c.primary else c.border
    Column(
        modifier
            .background(background, Radius.md)
            .border(1.dp, border, Radius.md)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(kpi.label, color = if (kpi.main) Color.White else c.textSecondary, fontSize = 12.sp)
        Text(
            kpi.value,
            color = if (kpi.main) Color.White else c.text,
            fontSize = if (kpi.main) 22.sp else 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

data class StatMeter(val ratio: Double, val tone: StatTone = StatTone.Neutral)

@Composable
fun StatItem(
    title: String,
    value: String,
    detail: String = "",
    meter: StatMeter? = null,
    divider: Boolean = true
) {
    val c = Ejden.colors
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, modifier = Modifier.weight(1f), color = c.text, fontSize = 14.sp)
                Text(value, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold, softWrap = false)
            }
            if (detail.isNotEmpty()) {
                Text(detail, modifier = Modifier.padding(top = 2.dp), color = c.textSecondary, fontSize = 12.sp)
            }
            if (meter != null) {
                val fill = when (meter.tone) {
                    StatTone.Neutral -> c.primary
                    StatTone.Good -> c.success
                    StatTone.Warn -> c.warning
                    StatTone.Bad -> c.error
                }
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(c.border, CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(meter.ratio.coerceIn(0.02, 1.0).toFloat())
                            .fillMaxHeight()
                            .background(fill, CircleShape)
                    )
                }
            }
        }
        if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
    }
}

@Composable
fun StatLegend() {
    val c = Ejden.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(c.primary, RoundedCornerShape(2.dp)))
            Text("Réalisé", modifier = Modifier.padding(start = 4.dp), color = c.textSecondary, fontSize = 12.sp)
        }
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(c.primarySoft, RoundedCornerShape(2.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = c.primary,
                            cornerRadius = CornerRadius(2.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))
                            )
                        )
                    }
            )
            Text(
                "Prévision (trait orange : fourchette probable)",
                modifier = Modifier.padding(start = 4.dp),
                color = c.textSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Histogramme avec barres de prévision (pointillées) et fourchette d'incertitude. */
@Composable
fun StatBarChart(
    labels: List<String>,
    values: List<Double>,
    forecast: List<Double>,
    forecastLabels: List<String>,
    sd: Double
) {
    val c = Ejden.colors
    val measurer = rememberTextMeasurer()

    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(340f / 170f)
            .semantics { contentDescription = "Graphique des ventes et prévision" }
    ) {
        val s = size.width / 340f
        val w = 340f
        val h = 170f
        val left = 4f
        val top = 14f
        val bottom = 22f
        val all = values + forecast
        val maxV = (maxOf(1.0, all.maxOrNull() ?: 0.0, forecast.maxOfOrNull { it + sd } ?: 0.0) * 1.08).toFloat()
        val slot = (w - left * 2) / all.size
        val barW = minOf(26f, slot * 0.7f)
        val plotH = h - top - bottom
        val textStyle = TextStyle(color = c.textSecondary, fontSize = (9f * s).toSp())

        fun yOf(v: Double): Float = top + plotH * (1f - (v / maxV).toFloat())

        fun drawLabel(text: String, x: Float, baseline: Float, center: Boolean) {
            val layout = measurer.measure(text, textStyle)
            val dx = if (center) layout.size.width / 2f else 0f
            drawText(layout, topLeft = Offset(x * s - dx, baseline * s - layout.firstBaseline))
        }

        drawLine(
            c.border,
            Offset(left * s, (top + plotH) * s),
            Offset((w - left) * s, (top + plotH) * s),
            strokeWidth = 1f * s
        )
        drawLabel(statCompact(maxV.toDouble()), left, 9f, false)

        all.forEachIndexed { i, value ->
            val isForecast = i >= values.size
            val x = left + slot * i + (slot - barW) / 2f
            val y = yOf(value)
            val barH = maxOf(1f, top + plotH - y)
            val topLeft = Offset(x * s, y * s)
            val barSize = Size(barW * s, barH * s)
            val radius = CornerRadius(2f * s)

            if (isForecast) {
                drawRoundRect(c.primarySoft, topLeft, barSize, radius)
                drawRoundRect(
                    c.primary, topLeft, barSize, radius,
                    style = Stroke(
                        width = 1f * s,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * s, 2f * s))
                    )
                )
            } else {
                drawRoundRect(c.primary, topLeft, barSize, radius)
            }

            val text = if (isForecast) forecastLabels[i - values.size] else labels[i]
            if (all.size <= 16 || i % 2 == 0 || isForecast) {
                drawLabel(text, x + barW / 2f, h - 8f, true)
            }

            if (isForecast) {
                val cx = x + barW / 2f
                drawLine(
                    c.warning,
                    Offset(cx * s, yOf(value + sd) * s),
                    Offset(cx * s, yOf(maxOf(0.0, value - sd)) * s),
                    strokeWidth = 1.5f * s
                )
                drawLabel(statCompact(value), cx, maxOf(10f, yOf(value + sd) - 3f), true)
            }
        }
    }
}
