package com.ejden.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.data.PeriodChoice
import com.ejden.app.data.PeriodPreset
import com.ejden.app.data.ResolvedPeriod
import com.ejden.app.data.isValidDay
import com.ejden.app.ui.components.AppIcons
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val PRESET_OPTIONS = listOf(
    PeriodPreset.Today to "Aujourd'hui",
    PeriodPreset.Days7 to "7 derniers jours",
    PeriodPreset.Month1 to "1 mois",
    PeriodPreset.Months3 to "3 mois",
    PeriodPreset.Months6 to "6 mois",
    PeriodPreset.Custom to "Choisir les dates"
)

private fun displayDay(iso: String): String = try {
    LocalDate.parse(iso).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
} catch (e: Exception) {
    ""
}

@Composable
fun PeriodSheet(
    visible: Boolean,
    current: PeriodChoice,
    resolved: ResolvedPeriod,
    onClose: () -> Unit,
    onApply: (PeriodChoice) -> Unit
) {
    val c = Ejden.colors
    val reduce = Ejden.reduceMotion
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val offsetPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    val duration = if (reduce) 1 else 180

    // Ces états repartent de zéro à chaque ouverture.
    var selected by remember(visible) { mutableStateOf(current.preset) }
    var showCustom by remember(visible) { mutableStateOf(current.preset == PeriodPreset.Custom) }
    var from by remember(visible) { mutableStateOf(current.from ?: resolved.startDay.toString()) }
    var to by remember(visible) { mutableStateOf(current.to ?: resolved.endDay.toString()) }
    var error by remember(visible) { mutableStateOf("") }
    var picking by remember(visible) { mutableIntStateOf(0) } // 0 : rien, 1 : début, 2 : fin

    Box(Modifier.fillMaxSize()) {
        if (visible) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF10181C).copy(alpha = 0.45f))
                    .tap(onClose)
            )
        }

        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(duration)) + slideInVertically(tween(duration)) { offsetPx },
            exit = ExitTransition.None
        ) {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .background(c.surface, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .tap { }
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp + navInset)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Période affichée", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(c.surface, RoundedCornerShape(12.dp))
                            .border(1.dp, c.border, RoundedCornerShape(12.dp))
                            .tap(onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        StrokeIcon(AppIcons.Close, c.text, 20.dp, strokeWidth = 2f)
                    }
                }
                Spacer(Modifier.height(12.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((preset, label) in PRESET_OPTIONS) {
                        val isSelected = selected == preset
                        val shape = RoundedCornerShape(13.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 50.dp)
                                .background(if (isSelected) c.primarySoft else c.surface, shape)
                                .border(1.dp, if (isSelected) c.primary else c.border, shape)
                                .tap {
                                    if (preset == PeriodPreset.Custom) {
                                        selected = PeriodPreset.Custom
                                        showCustom = true
                                    } else {
                                        onApply(PeriodChoice(preset))
                                    }
                                }
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val color = if (isSelected) c.primary else c.text
                            Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight(600))
                            if (isSelected) {
                                Text("✓", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (showCustom) {
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
                    Spacer(Modifier.height(14.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DateField("Du", from, Modifier.weight(1f)) { picking = 1 }
                        DateField("Au", to, Modifier.weight(1f)) { picking = 2 }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        error,
                        modifier = Modifier.heightIn(min = 16.dp),
                        color = c.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight(600)
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .background(c.primary, RoundedCornerShape(13.dp))
                            .tap {
                                val today = LocalDate.now()
                                error = when {
                                    !isValidDay(from) || !isValidDay(to) -> "Choisissez les deux dates."
                                    from > to -> "La date de début doit précéder la date de fin."
                                    LocalDate.parse(to) > today -> "Les dates à venir ne peuvent pas être choisies."
                                    else -> ""
                                }
                                if (error.isEmpty()) onApply(PeriodChoice(PeriodPreset.Custom, from, to))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Afficher cette période", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (picking != 0) {
        DayPickerDialog(
            initialDay = if (picking == 1) from else to,
            onPick = { day -> if (picking == 1) from = day else to = day },
            onDismiss = { picking = 0 }
        )
    }
}

@Composable
private fun DateField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = c.textSecondary, fontSize = 12.sp, fontWeight = FontWeight(600))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .background(c.surface, shape)
                .border(1.dp, c.border, shape)
                .tap(onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(displayDay(value), color = c.text, fontSize = 15.sp)
        }
    }
}

/** Sélecteur de jour : les dates à venir sont interdites (aucune vente possible). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPickerDialog(initialDay: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val initialMillis = try {
        LocalDate.parse(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
        }
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    onDismiss()
                }
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    ) {
        DatePicker(state = state)
    }
}
