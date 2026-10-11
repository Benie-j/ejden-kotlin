package com.ejden.app.ui.dashboard

import com.ejden.app.ui.nav.Routes

/** soon = true : module sans page (affiche « Bientôt », comme href: null dans modules.ts). */
data class AppModule(
    val id: String,
    val label: String,
    val description: String,
    val soon: Boolean = false
)

data class ModuleCategory(val label: String, val modules: List<AppModule>)

val MODULE_CATEGORIES: List<ModuleCategory> = listOf(
    ModuleCategory(
        "Gestion",
        listOf(
            AppModule("produits", "Produits", "Catalogue et prix"),
            AppModule("clients", "Clients", "Fiches et crédits"),
            AppModule("stock", "Stock", "Niveaux et mouvements"),
            AppModule("fournisseurs", "Fournisseurs", "Achats et dettes"),
            AppModule("commandes", "Commandes", "Clients et fournisseurs")
        )
    ),
    ModuleCategory(
        "Finances",
        listOf(
            AppModule("caisse", "Caisse", "Solde et mouvements"),
            AppModule("depenses", "Dépenses", "Charges du commerce"),
            AppModule("credits", "Crédits", "Sommes à encaisser"),
            AppModule("finances", "Finances", "Vue d'ensemble")
        )
    ),
    ModuleCategory(
        "Documents",
        listOf(
            AppModule("devis", "Devis", "Propositions de prix"),
            AppModule("factures", "Factures", "Documents de vente"),
            AppModule("recus", "Reçus", "Preuves de paiement")
        )
    ),
    ModuleCategory(
        "Analyse",
        listOf(
            AppModule("rapports", "Rapports", "Jour, mois, année"),
            AppModule("statistiques", "Statistiques", "Prévisions et analyses")
        )
    ),
    ModuleCategory(
        "Système",
        listOf(
            AppModule("notifications", "Notifications", "Alertes et rappels"),
            AppModule("sauvegarde", "Sauvegarde", "Copie des données"),
            AppModule("parametres", "Paramètres", "Boutique et reçus"),
            AppModule("aide", "Aide", "Questions fréquentes", soon = true)
        )
    )
)

/** Écran de destination d'un module ou d'une action rapide. */
fun moduleRoute(id: String): String = when (id) {
    "produits" -> Routes.PRODUCTS
    "ajouter-produit" -> Routes.PRODUCT_ADD
    "ventes" -> Routes.SALES
    "clients-new" -> Routes.CLIENTS + "?new=1"
    "depenses-new" -> Routes.EXPENSES + "?new=1"
    "caisse-new" -> Routes.CASH + "?new=in"
    else -> Routes.module(id)
}
