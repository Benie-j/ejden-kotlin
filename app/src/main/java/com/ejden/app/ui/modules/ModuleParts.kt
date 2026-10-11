package com.ejden.app.ui.modules

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.components.IconSpec
import com.ejden.app.ui.components.StrokeIcon
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.theme.Ejden

/** Liste déroulante d'un formulaire (le <select> de modules.css). */
@Composable
fun ModuleSelect(value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    var open by remember { mutableStateOf(false) }

    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .background(c.surface, shape)
                .border(1.dp, c.border, shape)
                .tap { open = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: "",
                modifier = Modifier.weight(1f),
                color = c.text,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            StrokeIcon(IconSpec(listOf("M6 9l6 6 6-6")), c.textSecondary, 18.dp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((id, label) in options) {
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        open = false
                        onSelect(id)
                    }
                )
            }
        }
    }
}

/** Titre d'un groupe de jour (« Aujourd'hui », « Hier », « Jeudi 1 Octobre 2026 »). */
@Composable
fun DayHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 18.dp, bottom = 8.dp),
        color = Ejden.colors.textSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold
    )
}

/** Carte sans détail (ligne qui ne se déplie pas). */
@Composable
fun StaticRowCard(content: @Composable RowScope.() -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .background(c.surface, shape)
            .border(1.dp, c.border, shape)
            .clip(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
