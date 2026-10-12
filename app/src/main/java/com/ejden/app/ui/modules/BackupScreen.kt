package com.ejden.app.ui.modules

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.BACKUP_FILE_MAX
import com.ejden.app.data.BackupFile
import com.ejden.app.data.BackupParse
import com.ejden.app.data.BackupSummary
import com.ejden.app.data.applyBackup
import com.ejden.app.data.backupFileName
import com.ejden.app.data.backupSummaryText
import com.ejden.app.data.createBackup
import com.ejden.app.data.formatBackupDate
import com.ejden.app.data.parseBackup
import com.ejden.app.data.saveMeta
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.returnTo
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

private class FileRead(val text: String?, val error: String?)

private fun readBackupText(context: Context, uri: Uri): FileRead {
    return try {
        val size = context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        if (size > BACKUP_FILE_MAX) return FileRead(null, "Fichier trop volumineux.")
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        if (text == null) FileRead(null, "Impossible de lire le fichier.") else FileRead(text, null)
    } catch (e: Exception) {
        FileRead(null, "Impossible de lire le fichier.")
    }
}

@Composable
fun BackupScreen(nav: NavController) {
    val context = LocalContext.current
    val app = context.applicationContext as EjdenApp
    val c = Ejden.colors
    val toast = rememberModuleToast()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var meta by remember { mutableStateOf(app.backup.getMeta()) }
    var pending by remember { mutableStateOf<BackupFile?>(null) }
    var pendingSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun proposeRestore(text: String) {
        when (val result = parseBackup(text)) {
            is BackupParse.Failed -> toast.show(result.error)
            is BackupParse.Ok -> {
                pending = result.backup
                pendingSummary = result.summary
                scope.launch { listState.animateScrollToItem(6) }
            }
        }
    }

    fun markFileBackup(createdAt: String) {
        app.backup.saveMeta(lastFile = createdAt)
        meta = app.backup.getMeta()
    }

    // Choisir un fichier de sauvegarde à restaurer.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val read = withContext(Dispatchers.IO) { readBackupText(context, uri) }
                busy = false
                if (read.text == null) toast.show(read.error ?: "Impossible de lire le fichier.") else proposeRestore(read.text)
            }
        }
    }

    // Enregistrer la sauvegarde à l'endroit choisi sur le téléphone.
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val backup = withContext(Dispatchers.IO) { createBackup(app.store) }
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(backup.toJsonText().toByteArray(Charsets.UTF_8)) }
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                busy = false
                if (ok) {
                    markFileBackup(backup.createdAt)
                    toast.show("Sauvegarde enregistrée.")
                } else {
                    toast.show("Impossible de créer le fichier.")
                }
            }
        }
    }

    // Menu de partage Android : WhatsApp, e-mail, Drive, Fichiers...
    fun shareBackup() {
        scope.launch {
            busy = true
            try {
                val backup = withContext(Dispatchers.IO) { createBackup(app.store) }
                val uri = withContext(Dispatchers.IO) {
                    val dir = File(context.cacheDir, "backups").apply { mkdirs() }
                    dir.listFiles()?.forEach { it.delete() }
                    val file = File(dir, backupFileName(LocalDate.now()))
                    file.writeText(backup.toJsonText(), Charsets.UTF_8)
                    FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                }
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(send, "Sauvegarde EJDEN"))
                markFileBackup(backup.createdAt)
                toast.show("Sauvegarde prête.")
            } catch (e: Exception) {
                toast.show("Impossible de créer le fichier.")
            } finally {
                busy = false
            }
        }
    }

    fun confirmRestore() {
        val backup = pending ?: return
        scope.launch {
            busy = true
            val ok = withContext(Dispatchers.IO) { applyBackup(app.store, backup) }
            busy = false
            pending = null
            pendingSummary = null
            if (!ok) {
                toast.show("Restauration impossible : l'état précédent a été conservé.")
                return@launch
            }
            // Thème et taille du texte restaurés : ils s'appliquent tout de suite.
            app.appearanceState.value = app.appearance.get()
            meta = app.backup.getMeta()
            toast.show("Données restaurées.")
            delay(1200)
            nav.returnTo(Routes.DASHBOARD)
        }
    }

    ModuleScaffold("SYSTÈME", "Sauvegarde", { nav.goBack() }, toast, listState) {
        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackupCard("Google Drive", formatBackupDate(meta.lastDrive), Modifier.weight(1f).fillMaxHeight())
                BackupCard("Fichier", formatBackupDate(meta.lastFile), Modifier.weight(1f).fillMaxHeight())
            }
        }

        item {
            ModuleSection("Google Drive") {
                Text(
                    "Une copie de toutes vos données est envoyée dans un espace privé de votre Drive, " +
                        "que seul EJDEN peut lire.",
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.5.sp
                )
                // La connexion Google native demande son propre module : boutons grisés pour l'instant.
                Text(
                    "Sauvegarde Google Drive : bientôt disponible.",
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = c.text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight(600)
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleActionButton("Sauvegarder sur Drive", ModuleButtonStyle.Primary, enabled = false) {}
                    ModuleActionButton("Restaurer depuis Drive", ModuleButtonStyle.Normal, enabled = false) {}
                }
            }
        }

        item {
            ModuleSection("Fichier de sauvegarde") {
                Text(
                    "Enregistrez la copie sur le téléphone ou envoyez-la vers Google Drive, WhatsApp " +
                        "ou un e-mail avec le menu de partage.",
                    modifier = Modifier.padding(bottom = 12.dp),
                    color = c.textSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.5.sp
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleActionButton("Enregistrer / partager", ModuleButtonStyle.Primary, enabled = !busy) { shareBackup() }
                    ModuleActionButton("Enregistrer sur le téléphone", ModuleButtonStyle.Normal, enabled = !busy) {
                        saveLauncher.launch(backupFileName(LocalDate.now()))
                    }
                    ModuleActionButton("Restaurer depuis un fichier", ModuleButtonStyle.Normal, enabled = !busy) {
                        importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    }
                }
            }
        }

        val backup = pending
        val summary = pendingSummary
        if (backup != null && summary != null) {
            item {
                Column(
                    Modifier
                        .padding(bottom = 14.dp)
                        .fillMaxWidth()
                        .background(c.surface, RoundedCornerShape(16.dp))
                        .border(1.dp, c.error, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text("Restaurer cette sauvegarde ?", color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        backupSummaryText(summary, backup.createdAt),
                        modifier = Modifier.padding(top = 12.dp, bottom = 12.dp),
                        color = c.textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 19.5.sp
                    )
                    Text(
                        "Les données actuelles de l'appareil seront remplacées. Sauvegardez-les d'abord si besoin.",
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .fillMaxWidth()
                            .background(c.errorSoft, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        color = c.error,
                        fontSize = 13.sp,
                        fontWeight = FontWeight(600)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModuleActionButton("Oui, restaurer", ModuleButtonStyle.DangerSolid, enabled = !busy) { confirmRestore() }
                        ModuleActionButton("Annuler", ModuleButtonStyle.Normal) {
                            pending = null
                            pendingSummary = null
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupCard(label: String, value: String, modifier: Modifier) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(10.dp)
    Column(modifier.background(c.surface, shape).border(1.dp, c.border, shape).padding(14.dp)) {
        Text(label, color = c.textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
