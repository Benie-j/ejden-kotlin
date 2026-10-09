package com.ejden.app.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ejden.app.ui.intro.IntroScreen
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.EjdenTheme

@Composable
fun EjdenNav() {
    val nav = rememberNavController()

    NavHost(
        navController = nav,
        startDestination = Routes.INTRO,
        enterTransition = { fadeIn(tween(120)) },
        exitTransition = { fadeOut(tween(90)) },
        popEnterTransition = { fadeIn(tween(120)) },
        popExitTransition = { fadeOut(tween(90)) }
    ) {
        composable(Routes.INTRO) {
            IntroScreen(
                onStart = {
                    // Le splash sort de la pile : on n'y revient plus avec Retour.
                    nav.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.INTRO) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.DASHBOARD) {
            EjdenTheme { DashboardPlaceholder(nav) }
        }
        composable(Routes.PRODUCTS) {
            EjdenTheme { ProductsPlaceholder(nav) }
        }
    }
}

// ---- Écrans provisoires (supprimés à l'étape 3b) ----

@Composable
private fun DashboardPlaceholder(nav: NavController) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }

    // Règle 4 : le panneau se ferme avant tout retour d'écran.
    BackHandler(enabled = menuOpen) { menuOpen = false }

    Box(Modifier.fillMaxSize().background(Ejden.colors.background)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Tableau de bord", color = Ejden.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("En construction", color = Ejden.colors.textSecondary, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { nav.open(Routes.PRODUCTS) }) { Text("Ouvrir Produits (test)") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { menuOpen = true }) { Text("Ouvrir le menu Plus (test)") }
        }

        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { menuOpen = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Ejden.colors.surface, RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp))
                        .padding(24.dp)
                ) {
                    Text("Menu Plus (test) : appuie sur Retour", color = Ejden.colors.text)
                }
            }
        }
    }
}

@Composable
private fun ProductsPlaceholder(nav: NavController) {
    Column(Modifier.fillMaxSize().background(Ejden.colors.background).statusBarsPadding()) {
        TextButton(onClick = { nav.goBack() }) { Text("← Retour") }
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Produits", color = Ejden.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("En construction", color = Ejden.colors.textSecondary, fontSize = 14.sp)
        }
    }
}
