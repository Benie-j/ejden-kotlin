package com.ejden.app.data

enum class ReportSectionId(val id: String, val label: String) {
    Sales("sales", "Ventes"),
    Products("products", "Produits"),
    Stock("stock", "Stock"),
    Clients("clients", "Clients"),
    Payments("payments", "Paiements"),
    Credits("credits", "Crédits"),
    Team("team", "Équipe"),
    Movements("movements", "Mouvements"),
    Analyses("analyses", "Analyses")
}

/** Qui peut voir/exporter quoi. Le propriétaire (seul utilisateur pour l'instant) a tous les droits. */
data class ReportPermissions(
    val viewReports: Boolean = true,
    val exportReports: Boolean = true,
    val printReports: Boolean = true,
    val viewFinancialReports: Boolean = true,
    val viewTeamReports: Boolean = true,
    val viewAdvancedAnalytics: Boolean = true
)

fun reportPermissionFor(id: ReportSectionId, permissions: ReportPermissions): Boolean {
    if (!permissions.viewReports) return false
    return when (id) {
        ReportSectionId.Payments, ReportSectionId.Credits -> permissions.viewFinancialReports
        ReportSectionId.Team -> permissions.viewTeamReports
        ReportSectionId.Analyses -> permissions.viewAdvancedAnalytics
        else -> true
    }
}
