package com.ejden.app.ui.dashboard

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.DashboardData
import com.ejden.app.data.formatNumber
import com.ejden.app.data.greetingFor
import com.ejden.app.data.resolvePeriod
import com.ejden.app.ui.components.tap
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.nav.switchTab
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

@Composable
fun DashboardScreen(nav: NavController) {
    val app = LocalContext.current.applicationContext as EjdenApp
    val c = Ejden.colors

    // La salutation suit l'horloge (mise à jour chaque minute).
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = LocalDateTime.now()
        }
    }

    val shop = remember(now) { app.settings.getShop() }
    val user = remember(now) { app.settings.getUser() }

    val brandName = shop.name.ifEmpty { "EJDEN" }
    val greeting = if (user.firstName.isNotEmpty()) {
        greetingFor(now.hour) + ", " + user.firstName
    } else {
        greetingFor(now.hour)
    }
    val initial = user.firstName
        .ifEmpty { shop.name.ifEmpty { "E" } }
        .take(1)
        .uppercase()

    // Période affichée (mémorisée) et chiffres correspondants.
    var choice by remember { mutableStateOf(app.period.get()) }
    val period = remember(choice, now.toLocalDate()) { resolvePeriod(choice) }

    // Retour sur l'app (depuis l'arrière-plan) : on recalcule.
    var refreshKey by remember { mutableIntStateOf(0) }
    val activity = LocalContext.current as? ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshKey += 1
        }
        activity?.lifecycle?.addObserver(observer)
        onDispose { activity?.lifecycle?.removeObserver(observer) }
    }

    var data by remember { mutableStateOf(DashboardData.empty()) }
    LaunchedEffect(period, refreshKey) {
        data = withContext(Dispatchers.Default) { app.dashboard.load(period) }
    }

    // Panneaux ouverts (conservés à la rotation) et message temporaire.
    var quickOpen by rememberSaveable { mutableStateOf(false) }
    var moreOpen by rememberSaveable { mutableStateOf(false) }
    var periodOpen by rememberSaveable { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var toastToken by remember { mutableIntStateOf(0) }

    fun showToast(text: String) {
        toastText = text
        toastVisible = true
        toastToken += 1
    }

    LaunchedEffect(toastToken) {
        if (toastToken > 0) {
            delay(2500)
            toastVisible = false
        }
    }

    // Retour : un panneau ouvert se ferme avant toute autre action.
    BackHandler(enabled = quickOpen) { quickOpen = false }
    BackHandler(enabled = moreOpen) { moreOpen = false }
    BackHandler(enabled = periodOpen) { periodOpen = false }

    val sidePadding = if (LocalConfiguration.current.screenWidthDp <= 430) 11.dp else 14.dp
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            Topbar(
                brandName = brandName,
                greeting = greeting,
                initial = initial,
                unread = 0, // branché avec le module Notifications
                onNotifications = { nav.open(Routes.module("notifications")) },
                onProfile = { nav.open(Routes.module("parametres")) }
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = sidePadding, end = sidePadding, top = 24.dp, bottom = 100.dp + navInset)
            ) {
                DashboardHeader(
                    periodLabel = period.buttonLabel,
                    onPeriod = { periodOpen = true },
                    onNewSale = { nav.open(Routes.module("ventes")) }
                )
                Spacer(Modifier.height(20.dp))

                KpiGrid(data, period)
                Spacer(Modifier.height(16.dp))

                CashPanel(data, period, onOpen = { nav.open(Routes.module("caisse")) })
                Spacer(Modifier.height(16.dp))

                StockPanel(data.stock, onOpen = { nav.open(Routes.module("stock")) })
                Spacer(Modifier.height(16.dp))

                val clientsCount = data.clientsCount
                val receivables = data.receivables
                val alerts = data.stock.lowCount + data.stock.outCount

                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    MiniPanel(
                        label = "CLIENTS",
                        title = "Clients actifs",
                        value = clientsCount.toString(),
                        description = if (clientsCount > 1) "clients enregistrés" else "client enregistré",
                        link = "Gérer les clients",
                        onLink = { nav.open(Routes.module("clients")) }
                    )
                    MiniPanel(
                        label = "CRÉANCES",
                        title = "À encaisser",
                        value = if (receivables.amount > 0) formatNumber(receivables.amount) else "\u2014",
                        description = if (receivables.amount > 0) {
                            "FCFA · ${receivables.debtorsCount} débiteur" +
                                if (receivables.debtorsCount > 1) "s" else ""
                        } else {
                            "FCFA"
                        },
                        link = "Voir les crédits",
                        onLink = { nav.open(Routes.module("credits")) }
                    )
                    MiniPanel(
                        label = "ATTENTION",
                        title = "Alertes",
                        value = alerts.toString(),
                        description = if (alerts == 0) {
                            "aucune alerte"
                        } else {
                            "produit" + (if (alerts > 1) "s" else "") + " à réapprovisionner"
                        },
                        link = "Voir les alertes",
                        onLink = { nav.open(Routes.module("stock")) },
                        labelColor = c.warning
                    )
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Graphique, dernières ventes et activité récente : migration en cours.",
                    color = c.textLight,
                    fontSize = 12.sp
                )
            }
        }

        BottomNav(
            onHome = { nav.switchTab(Routes.DASHBOARD) },
            onSales = { nav.switchTab(Routes.module("historique-ventes")) },
            onAdd = { quickOpen = true },
            onProducts = { nav.switchTab(Routes.PRODUCTS) },
            onMore = { moreOpen = true },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        QuickActionsSheet(
            visible = quickOpen,
            onClose = { quickOpen = false },
            onSelect = { id ->
                quickOpen = false
                nav.open(moduleRoute(id))
            }
        )

        MoreMenuSheet(
            visible = moreOpen,
            onClose = { moreOpen = false },
            onOpen = { id ->
                moreOpen = false
                nav.open(moduleRoute(id))
            },
            onSoon = { label ->
                moreOpen = false
                showToast("$label : bientôt disponible.")
            }
        )

        PeriodSheet(
            visible = periodOpen,
            current = choice,
            resolved = period,
            onClose = { periodOpen = false },
            onApply = { newChoice ->
                if (!app.period.save(newChoice)) showToast("Impossible d'enregistrer la période.")
                choice = newChoice
                periodOpen = false
            }
        )

        AppToast(
            text = toastText,
            visible = toastVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp + navInset)
        )
    }
}

@Composable
private fun DashboardHeader(periodLabel: String, onPeriod: () -> Unit, onNewSale: () -> Unit) {
    val c = Ejden.colors
    val compact = LocalConfiguration.current.screenWidthDp <= 430

    Column(Modifier.fillMaxWidth()) {
        Text(
            "TABLEAU DE BORD",
            color = c.primary,
            fontSize = 9.sp,
            fontWeight = FontWeight(800),
            letterSpacing = 0.13.em
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Vue générale",
            color = c.text,
            fontSize = 25.sp,
            lineHeight = 28.75.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.02).em
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Suivez votre activité et prenez les bonnes décisions.",
            color = c.textSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(16.dp))

        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(periodLabel, onPeriod)
                PrimaryAction("+ Nouvelle vente", onNewSale)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { DateButton(periodLabel, onPeriod) }
                Box(Modifier.weight(1f)) { PrimaryAction("+ Nouvelle vente", onNewSale) }
            }
        }
    }
}

@Composable
private fun DateButton(label: String, onClick: () -> Unit) {
    val c = Ejden.colors
    val shape = RoundedCornerShape(9.dp)
    val background = if (Ejden.dark) Color(0xFF111B1F) else c.surface

    Row(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(background, shape)
            .border(1.dp, c.border, shape)
            .tap(onClick)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(label, color = c.textSecondary, fontSize = 11.sp, fontWeight = FontWeight(600))
        Caret(c.textSecondary)
    }
}

/** Petite flèche vers le bas (le « date-caret » du CSS). */
@Composable
private fun Caret(color: Color) {
    Canvas(Modifier.size(width = 10.dp, height = 8.dp)) {
        val path = Path().apply {
            moveTo(0.8.dp.toPx(), 1.2.dp.toPx())
            lineTo(5.dp.toPx(), 5.4.dp.toPx())
            lineTo(9.2.dp.toPx(), 1.2.dp.toPx())
        }
        drawPath(
            path,
            color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Butt, join = StrokeJoin.Miter)
        )
    }
}

@Composable
private fun PrimaryAction(label: String, onClick: () -> Unit) {
    val c = Ejden.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        if (pressed) c.primaryDark else c.primary, tween(200), label = "primaryAction"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(background, RoundedCornerShape(9.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
