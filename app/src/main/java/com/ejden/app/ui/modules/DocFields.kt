package com.ejden.app.ui.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** « 2026-10-20 » → « 20/10/2026 ». */
internal fun docDayText(day: String): String =
    LocalDate.parse(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

/** Nombre saisi (virgule ou point acceptés) ; vide = 0 ; illisible = NaN. */
internal fun parseDocNumber(text: String): Double {
    val clean = text.replace(Regex("\\s"), "").replace(",", ".")
    if (clean.isEmpty()) return 0.0
    return clean.toDoubleOrNull() ?: Double.NaN
}

/** 18.0 → « 18 », 5.5 → « 5.5 » (comme l'affichage d'un nombre en JavaScript). */
internal fun formatPlainNumber(value: Double): String =
    if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

/** Zone de texte sur plusieurs lignes (adresse, notes, conditions). */
@Composable
internal fun DocTextArea(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    rows: Int,
    maxLength: Int
) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var focused by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .background(c.surface, shape)
            .border(if (focused) 2.dp else 1.dp, if (focused) c.primary else c.border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onChange(it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            minLines = rows,
            textStyle = TextStyle(color = c.text, fontSize = 14.sp),
            cursorBrush = SolidColor(c.primary),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = Color(0xFF757575), fontSize = 14.sp)
                    inner()
                }
            }
        )
    }
}

/** Champ date (touche pour choisir) : affiche jj/mm/aaaa. */
@Composable
internal fun DocDateField(day: String, invalid: Boolean, onOpen: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(c.surface, shape)
            .border(1.dp, if (invalid) c.error else c.border, shape)
            .tap(onOpen)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val text = try {
            if (day.isEmpty()) "jj/mm/aaaa" else docDayText(day)
        } catch (e: Exception) {
            "jj/mm/aaaa"
        }
        Text(text, color = if (day.isEmpty()) c.textSecondary else c.text, fontSize = 14.sp)
    }
}

/** Sélecteur de jour (calendrier). Rend la date au format AAAA-MM-JJ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocDayPickerDialog(initialDay: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val initialMillis = try {
        LocalDate.parse(initialDay).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: Exception) {
        LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

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
