package com.ejden.app.ui.nav

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ejden.app.EjdenApp
import com.ejden.app.ui.dashboard.DashboardScreen
import com.ejden.app.ui.intro.IntroScreen
import com.ejden.app.ui.products.ProductsScreen
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.EjdenTheme

@Composable
fun EjdenNav() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as EjdenApp
    val appearance = remember { app.appearance.get() }

    NavHost(
        navController = nav,
        startDestination = Routes.INTRO,
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) }
    ) {
        composable(Routes.INTRO) {
            // L'accueil suit le thème de l'appareil (comme index.html) : pas de EjdenTheme.
            IntroScreen(
                onStart = {
                    nav.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.INTRO) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            EjdenTheme(appearance) { DashboardScreen(nav) }
        }
        composable(Routes.PRODUCTS) {
            EjdenTheme(appearance, page = true) { ProductsScreen(nav) }
        }
        composable(
            Routes.MODULE,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            EjdenTheme(appearance, page = true) { ModulePlaceholder(nav, moduleTitle(id)) }
        }
    }
}

// Écran provisoire des modules pas encore migrés.
@Composable
private fun ModulePlaceholder(nav: NavController, title: String) {
    Column(Modifier.fillMaxSize().background(Ejden.colors.background).statusBarsPadding()) {
        TextButton(onClick = { nav.goBack() }) { Text("← Retour") }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, color = Ejden.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("En construction", color = Ejden.colors.textSecondary, fontSize = 14.sp)
        }
    }
}
