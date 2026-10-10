package com.ejden.app.ui.nav

import androidx.navigation.NavController

object Routes {
    const val INTRO = "intro"
    const val DASHBOARD = "dashboard"
    const val PRODUCTS = "produits"
    const val MODULE = "module/{id}"
    const val SALES = "ventes"
    const val PRODUCT_ADD = "produits/ajouter"
    const val PRODUCT_EDIT = "produits/modifier/{id}"

    fun productEdit(id: String): String = "produits/modifier/" + android.net.Uri.encode(id)

    /** Module pas encore migré : écran « En construction ». */
    fun module(id: String): String = "module/$id"
}

private val MODULE_TITLES = mapOf(
    "produits" to "Produits",
    "clients" to "Clients",
    "stock" to "Stock",
    "fournisseurs" to "Fournisseurs",
    "commandes" to "Commandes",
    "caisse" to "Caisse",
    "depenses" to "Dépenses",
    "credits" to "Crédits",
    "finances" to "Finances",
    "devis" to "Devis",
    "factures" to "Factures",
    "recus" to "Reçus",
    "rapports" to "Rapports",
    "statistiques" to "Statistiques",
    "notifications" to "Notifications",
    "sauvegarde" to "Sauvegarde",
    "parametres" to "Paramètres",
    "ventes" to "Nouvelle vente",
    "historique-ventes" to "Ventes",
    "ajouter-produit" to "Ajouter un produit",
    "modifier-produit" to "Modifier le produit"
)

fun moduleTitle(id: String): String = MODULE_TITLES[id] ?: id

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

/** Revient à un écran de la pile ; s'il n'y est pas, l'ouvre à la place de l'écran actuel. */
fun NavController.returnTo(route: String) {
    if (popBackStack(route, inclusive = false)) return
    val current = currentBackStackEntry?.destination?.route
    navigate(route) {
        if (current != null) popUpTo(current) { inclusive = true }
        launchSingleTop = true
    }
}
