package com.ejden.app.ui.nav

import androidx.navigation.NavController

object Routes {
    const val INTRO = "intro"
    const val DASHBOARD = "dashboard"
    const val PRODUCTS = "produits"
}

/** Ouvre un écran par-dessus l'actuel (jamais deux fois le même au sommet). */
fun NavController.open(route: String) {
    navigate(route) { launchSingleTop = true }
}

/** Onglet de la barre du bas : Retour ramène à l'Accueil. */
fun NavController.switchTab(route: String) {
    if (route == Routes.DASHBOARD) {
        popBackStack(Routes.DASHBOARD, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Revient à un écran précis de la pile ; s'il n'y est pas, l'ouvre. */
fun NavController.backTo(route: String) {
    if (!popBackStack(route, inclusive = false)) open(route)
}

/** Flèche de l'en-tête = bouton retour du système. Sans écran précédent : Accueil. */
fun NavController.goBack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(Routes.DASHBOARD) { launchSingleTop = true }
    }
}
