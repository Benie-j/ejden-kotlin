package com.ejden.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.ejden.app.EjdenApp
import com.ejden.app.data.greetingFor
import com.ejden.app.ui.nav.Routes
import com.ejden.app.ui.nav.open
import com.ejden.app.ui.nav.switchTab
import com.ejden.app.ui.theme.Ejden
import kotlinx.coroutines.delay
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
                DashboardHeader(onNewSale = { nav.open(Routes.module("ventes")) })
                Spacer(Modifier.height(20.dp))
                Text(
                    "Indicateurs et graphiques : migration en cours.",
                    color = c.textLight,
                    fontSize = 12.sp
                )
            }
        }

        BottomNav(
            onHome = { nav.switchTab(Routes.DASHBOARD) },
            onSales = { nav.switchTab(Routes.module("historique-ventes")) },
            onAdd = { /* panneau « actions rapides » : étape 3b-2 */ },
            onProducts = { nav.switchTab(Routes.PRODUCTS) },
            onMore = { /* menu « Plus » : étape 3b-2 */ },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun DashboardHeader(onNewSale: () -> Unit) {
    val c = Ejden.colors

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
        PrimaryAction("+ Nouvelle vente", onNewSale)
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
