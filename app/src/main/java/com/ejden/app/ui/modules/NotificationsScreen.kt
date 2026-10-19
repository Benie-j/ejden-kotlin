package com.ejden.app.ui.modules

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.AppNotification
import com.ejden.app.data.NotifCategory
import com.ejden.app.data.NotifSeverity
import com.ejden.app.ui.components.rememberResumeKey
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.goBack
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Nombre d'alertes non lues (badge du tableau de bord), mis à jour au retour sur l'accueil. */
@Composable
fun rememberUnreadNotifications(nav: NavController): Int {
    val app = LocalContext.current.applicationContext as EjdenApp
    val resumeKey = rememberResumeKey()
    var reloadKey by remember { mutableIntStateOf(0) }
    var unread by remember { mutableIntStateOf(0) }

    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == Routes.DASHBOARD) reloadKey += 1
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }
    LaunchedEffect(resumeKey, reloadKey) {
        unread = withContext(Dispatchers.Default) { app.notifications.unreadCount() }
    }
    return unread
}

@Composable
fun NotificationsScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val toast = rememberModuleToast()
    val resumeKey = rememberResumeKey()

    var items by remember { mutableStateOf<List<AppNotification>>(emptyList()) }
    var settings by remember { mutableStateOf<Map<NotifCategory, Boolean>>(NotifCategory.entries.associateWith { true }) }
    var dismissedCount by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableIntStateOf(0) }

    // Retour sur cet écran (depuis une alerte ouverte) : on recalcule.
    DisposableEffect(nav) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == Routes.NOTIFICATIONS) reloadKey += 1
        }
        nav.addOnDestinationChangedListener(listener)
        onDispose { nav.removeOnDestinationChangedListener(listener) }
    }

    LaunchedEffect(resumeKey, reloadKey) {
        val data = withContext(Dispatchers.Default) {
            Triple(
                app.notifications.getNotifications(),
                app.notifications.getSettings(),
                app.notifications.dismissedCount()
            )
        }
        items = data.first
        settings = data.second
        dismissedCount = data.third
        loaded = true
    }

    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3000)
            confirmClear = false
        }
    }

    val unread = items.filter { !it.read }
    val shown = if (unreadOnly) unread else items

    ModuleScaffold("SYSTÈME", "Notifications", { nav.goBack() }, toast) {
        item {
            Row(
                Modifier.padding(bottom = 14.dp).fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard("Non lues", unread.size.toString(), Modifier.weight(1f).fillMaxHeight())
                SummaryCard(
                    "Urgentes",
                    items.count { it.severity == NotifSeverity.Danger }.toString(),
                    Modifier.weight(1f).fillMaxHeight(),
                    valueColor = Ejden.colors.error
                )
            }
        }

        item {
            Column(Modifier.padding(bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Toutes", !unreadOnly) { unreadOnly = false }
                    FilterPill("Non lues", unreadOnly) { unreadOnly = true }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModuleActionButton(
                        "Tout lire", ModuleButtonStyle.Normal, Modifier.weight(1f),
                        enabled = unread.isNotEmpty()
                    ) {
                        app.notifications.markRead(items.map { it.id })
                        reloadKey += 1
                        toast.show("Toutes les notifications sont lues.")
                    }
                    ModuleActionButton(
                        if (confirmClear) "Confirmer ?" else "Tout effacer",
                        if (confirmClear) ModuleButtonStyle.DangerSolid else ModuleButtonStyle.Danger,
                        Modifier.weight(1f),
                        enabled = items.isNotEmpty()
                    ) {
                        if (!confirmClear) {
                            confirmClear = true
                        } else {
                            confirmClear = false
                            app.notifications.dismiss(items.map { it.id })
                            reloadKey += 1
                            toast.show("Notifications effacées.")
                        }
                    }
                }
            }
        }

        if (loaded && shown.isEmpty()) {
            item {
                val filtered = unreadOnly && items.isNotEmpty()
                ModuleEmpty(
                    if (filtered) "Tout est lu" else "Aucune notification",
                    if (filtered) "Aucune notification non lue."
                    else "Tout est en ordre : rien à signaler pour le moment."
                )
            }
        }

        items(shown, key = { it.id }) { n ->
            NotificationRow(
                n,
                onOpen = {
                    app.notifications.markRead(listOf(n.id))
                    reloadKey += 1
                    nav.open(n.route)
                },
                onDismiss = {
                    app.notifications.dismiss(listOf(n.id))
                    reloadKey += 1
                    toast.show("Notification effacée.")
                }
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            ModuleSection("Types d'alertes") {
                for (category in NotifCategory.entries) {
                    SettingRow(category, settings[category] == true) { value ->
                        val next = settings.toMutableMap()
                        next[category] = value
                        if (app.notifications.saveSettings(next)) {
                            reloadKey += 1
                        } else {
                            toast.show("Impossible d'enregistrer le réglage.")
                        }
                    }
                }
                if (dismissedCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Réafficher les alertes effacées ($dismissedCount)") {
                        app.notifications.restoreDismissed()
                        reloadKey += 1
                        toast.show("Alertes réaffichées.")
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(12.dp)
    val accent = when (n.severity) {
        NotifSeverity.Danger -> c.error
        NotifSeverity.Warning -> c.warning
        NotifSeverity.Info -> c.primary
    }
    val soft = when (n.severity) {
        NotifSeverity.Danger -> c.errorSoft
        NotifSeverity.Warning -> c.warningSoft
        NotifSeverity.Info -> c.primarySoft
    }
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(if (n.read) Color.Transparent else c.surface, shape)
            .border(1.dp, c.border, shape)
            .clip(shape)
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
        Row(
            Modifier
                .weight(1f)
                .tap(onOpen)
                .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .background(if (n.read) Color.Transparent else c.primary, CircleShape)
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        n.severity.label,
                        modifier = Modifier.background(soft, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(n.category.label, color = c.textSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    n.title,
                    color = if (n.read) c.textSecondary else c.text,
                    fontSize = 14.sp,
                    fontWeight = if (n.read) FontWeight(600) else FontWeight.Bold
                )
                Text(
                    n.message,
                    modifier = Modifier.padding(top = 3.dp),
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.4.sp
                )
            }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.border))
        Box(Modifier.width(44.dp).fillMaxHeight().tap(onDismiss), contentAlignment = Alignment.Center) {
            Text("✕", color = c.textSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingRow(category: NotifCategory, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Ejden.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .tap { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(category.label, color = c.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(category.description, color = c.textSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        val offset by animateDpAsState(if (checked) 18.dp else 0.dp, tween(200), label = "switch")
        Box(
            Modifier
                .size(44.dp, 26.dp)
                .background(if (checked) c.primary else c.border, CircleShape)
                .padding(3.dp)
        ) {
            Box(Modifier.offset(x = offset).size(20.dp).background(Color.White, CircleShape))
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}
