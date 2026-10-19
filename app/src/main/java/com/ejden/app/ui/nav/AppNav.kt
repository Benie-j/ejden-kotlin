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
import androidx.compose.runtime.getValue
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
import com.ejden.app.ui.history.HistoryScreen
import com.ejden.app.ui.intro.IntroScreen
import com.ejden.app.ui.modules.BackupScreen
import com.ejden.app.ui.modules.CashScreen
import com.ejden.app.ui.modules.ClientsScreen
import com.ejden.app.ui.modules.CreditsScreen
import com.ejden.app.ui.modules.ExpensesScreen
import com.ejden.app.ui.modules.InvoicesScreen
import com.ejden.app.ui.modules.ReceiptsScreen
import com.ejden.app.ui.modules.FinancesScreen
import com.ejden.app.ui.modules.NotificationsScreen
import com.ejden.app.ui.modules.SettingsScreen
import com.ejden.app.ui.modules.StockScreen
import com.ejden.app.ui.modules.PurchasesScreen
import com.ejden.app.ui.modules.OrdersScreen
import com.ejden.app.ui.modules.QuotesScreen
import com.ejden.app.ui.modules.SuppliersScreen
import com.ejden.app.ui.products.ProductFormScreen
import com.ejden.app.ui.products.ProductsScreen
import com.ejden.app.ui.sales.SalesScreen
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.EjdenTheme

@Composable
fun EjdenNav() {
    val nav = rememberNavController()
    val app = LocalContext.current.applicationContext as EjdenApp
    val appearance by app.appearanceState

    // Argument facultatif « ?new= » : l'écran place le curseur dans le bon champ.
    val newArg = listOf(
        navArgument("new") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
    )

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
        composable(Routes.HISTORY) {
            EjdenTheme(appearance, mixed = true) { HistoryScreen(nav) }
        }
        composable(Routes.CLIENTS + "?new={new}", arguments = newArg) { entry ->
            val focusNew = entry.arguments?.getString("new") != null
            EjdenTheme(appearance, mixed = true) { ClientsScreen(nav, focusNew) }
        }
        composable(Routes.CREDITS) {
            EjdenTheme(appearance, mixed = true) { CreditsScreen(nav) }
        }
        composable(Routes.EXPENSES + "?new={new}", arguments = newArg) { entry ->
            val focusNew = entry.arguments?.getString("new") != null
            EjdenTheme(appearance, mixed = true) { ExpensesScreen(nav, focusNew) }
        }
        composable(Routes.CASH + "?new={new}", arguments = newArg) { entry ->
            val requested = entry.arguments?.getString("new")
            EjdenTheme(appearance, mixed = true) { CashScreen(nav, requested) }
        }
        composable(Routes.SETTINGS) {
            EjdenTheme(appearance, mixed = true) { SettingsScreen(nav) }
        }
        composable(Routes.BACKUP) {
            EjdenTheme(appearance, mixed = true) { BackupScreen(nav) }
        }
        composable(
            Routes.STOCK + "?filter={filter}",
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EjdenTheme(appearance, mixed = true) { StockScreen(nav, entry.arguments?.getString("filter")) }
        }
        composable(Routes.SUPPLIERS) {
            EjdenTheme(appearance, mixed = true) { SuppliersScreen(nav) }
        }
        composable(
            Routes.PURCHASES + "?supplier={supplier}",
            arguments = listOf(
                navArgument("supplier") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EjdenTheme(appearance, mixed = true) { PurchasesScreen(nav, entry.arguments?.getString("supplier")) }
        }
        composable(Routes.ORDERS) {
            EjdenTheme(appearance, mixed = true) { OrdersScreen(nav) }
        }
        composable(Routes.QUOTES) {
            EjdenTheme(appearance, mixed = true) { QuotesScreen(nav) }
        }
        composable(
            Routes.INVOICES + "?open={open}",
            arguments = listOf(
                navArgument("open") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EjdenTheme(appearance, mixed = true) { InvoicesScreen(nav, entry.arguments?.getString("open")) }
        }
        composable(
            Routes.RECEIPTS + "?open={open}",
            arguments = listOf(
                navArgument("open") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { entry ->
            EjdenTheme(appearance, mixed = true) { ReceiptsScreen(nav, entry.arguments?.getString("open")) }
        }
        composable(Routes.NOTIFICATIONS) {
            EjdenTheme(appearance, mixed = true) { NotificationsScreen(nav) }
        }
        composable(Routes.FINANCES) {
            EjdenTheme(appearance, mixed = true) { FinancesScreen(nav) }
        }
        composable(Routes.SALES) {
            EjdenTheme(appearance, page = true) { SalesScreen(nav) }
        }
        composable(Routes.PRODUCT_ADD) {
            EjdenTheme(appearance, page = true) { ProductFormScreen(nav, null) }
        }
        composable(
            Routes.PRODUCT_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType })
        ) { entry ->
            val id = entry.arguments?.getString("id") ?: ""
            EjdenTheme(appearance, page = true) { ProductFormScreen(nav, id) }
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
