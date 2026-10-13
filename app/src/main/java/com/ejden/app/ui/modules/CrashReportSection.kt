package com.ejden.app.ui.modules

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ejden.app.CrashLog
import com.ejden.app.ui.theme.Ejden

/** Affiche le détail de la dernière fermeture brutale, avec un bouton pour l'envoyer. */
@Composable
fun CrashReportSection() {
    val context = LocalContext.current
    val c = Ejden.colors
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    val text = report ?: return

    ModuleSection("Dernier incident") {
        Text(
            "L'application s'est fermée brutalement. Envoyez ce rapport pour qu'on puisse corriger le problème.",
            modifier = Modifier.padding(bottom = 12.dp),
            color = c.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.5.sp
        )
        SelectionContainer {
            Text(
                text.take(1200),
                modifier = Modifier
                    .padding(bottom = 12.dp)
                    .fillMaxWidth()
                    .background(c.background, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                color = c.text,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ModuleActionButton("Partager le rapport", ModuleButtonStyle.Primary) {
                try {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                    context.startActivity(Intent.createChooser(send, "Rapport d'incident EJDEN"))
                } catch (e: Exception) {
                    // aucune application de partage disponible
                }
            }
            ModuleActionButton("Effacer le rapport", ModuleButtonStyle.Normal) {
                CrashLog.clear(context)
                report = null
            }
        }
    }
}
