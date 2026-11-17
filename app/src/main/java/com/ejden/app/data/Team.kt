package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Le propriétaire de l'appareil : toujours administrateur, jamais supprimé. */
const val TEAM_OWNER_ID = "owner"

enum class TeamRole(val id: String, val label: String, val description: String) {
    Admin("admin", "Administrateur", "Gère l'activité, l'équipe et toutes les données."),
    Manager("gerant", "Gérant", "Suit les ventes, le stock et les finances."),
    Seller("vendeur", "Vendeur", "Enregistre les ventes et consulte les produits."),
    StockKeeper("stock", "Gestionnaire de stock", "Gère les produits, achats et mouvements de stock.");

    companion object {
        fun fromId(value: Any?): TeamRole? = entries.firstOrNull { it.id == value }
    }
}

enum class TeamPermission(val id: String, val label: String) {
    Sell("vendre", "Ventes, clients, devis, factures et reçus"),
    ViewProducts("produits-voir", "Consulter les produits"),
    ManageProducts("produits-gerer", "Ajouter, modifier et supprimer des produits"),
    ManageStock("stock", "Stock, achats, fournisseurs et commandes"),
    Finances("finances", "Caisse, dépenses, crédits et finances"),
    Reports("rapports", "Statistiques et rapports"),

    /** Paramètres, équipe et sauvegarde : réservé aux administrateurs. */
    Settings("parametres", "Paramètres, équipe et sauvegarde");

    companion object {
        fun fromId(value: Any?): TeamPermission? = entries.firstOrNull { it.id == value }
    }
}

fun defaultPermissions(role: TeamRole): Set<TeamPermission> = when (role) {
    TeamRole.Admin -> TeamPermission.entries.toSet()
    TeamRole.Manager -> setOf(
        TeamPermission.Sell, TeamPermission.ViewProducts, TeamPermission.ManageStock,
        TeamPermission.Finances, TeamPermission.Reports
    )
    TeamRole.Seller -> setOf(TeamPermission.Sell, TeamPermission.ViewProducts)
    TeamRole.StockKeeper -> setOf(
        TeamPermission.ViewProducts, TeamPermission.ManageProducts, TeamPermission.ManageStock
    )
}

fun ownerDisplayName(firstName: String, lastName: String): String =
    (firstName + " " + lastName).jsTrim().ifEmpty { "Vous" }

data class TeamMember(
    val id: String,
    val name: String,
    val role: TeamRole,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("role", role.id)
        put("createdAt", createdAt)
    }
}

fun normalizeTeamMember(value: Any?): TeamMember? {
    val m = value as? JSONObject ?: return null
    val id = m.opt("id") as? String
    val name = cleanLabel(m.opt("name"), ModuleLimits.TEXT_MAX)
    val role = TeamRole.fromId(m.opt("role"))
    val createdAt = validDate(m.opt("createdAt"))
    if (id == null || id.isEmpty() || id.length > 100 || name.isEmpty() || role == null || createdAt == null) {
        return null
    }
    return TeamMember(id, name, role, createdAt)
}

enum class AddMemberResult { Ok, EmptyName, Duplicate, Failed }

class TeamRepository(private val store: KeyValueStore) {
    companion object {
        const val MEMBERS_KEY = "ejden_team"
        const val ACTIVE_KEY = "ejden_team_active"
        const val ROLES_KEY = "ejden_team_roles"
    }

    fun getMembers(): List<TeamMember> = readList(store, MEMBERS_KEY, ::normalizeTeamMember)

    private fun saveMembers(members: List<TeamMember>): Boolean =
        writeList(store, MEMBERS_KEY, members.map { it.toJson() })

    fun addMember(nameRaw: String, role: TeamRole): AddMemberResult {
        val name = cleanLabel(nameRaw, ModuleLimits.TEXT_MAX)
        if (name.isEmpty()) return AddMemberResult.EmptyName
        val members = getMembers()
        if (members.any { it.name.equals(name, ignoreCase = true) }) return AddMemberResult.Duplicate
        val member = TeamMember(newId(), name, role, toIso(Instant.now()))
        return if (saveMembers(members + member)) AddMemberResult.Ok else AddMemberResult.Failed
    }

    fun removeMember(id: String): Boolean {
        val members = getMembers()
        val remaining = members.filter { it.id != id }
        if (remaining.size == members.size) return false
        if (!saveMembers(remaining)) return false
        if (store.get(ACTIVE_KEY) == id) store.remove(ACTIVE_KEY)
        return true
    }

    fun setMemberRole(id: String, role: TeamRole): Boolean {
        val members = getMembers()
        if (members.none { it.id == id }) return false
        return saveMembers(members.map { if (it.id == id) it.copy(role = role) else it })
    }

    fun getActiveId(): String {
        val stored = store.get(ACTIVE_KEY)
        return if (stored != null && getMembers().any { it.id == stored }) stored else TEAM_OWNER_ID
    }

    fun setActive(id: String): Boolean {
        if (id == TEAM_OWNER_ID) {
            store.remove(ACTIVE_KEY)
            return true
        }
        if (getMembers().none { it.id == id }) return false
        return store.set(ACTIVE_KEY, id)
    }

    fun getActiveRole(): TeamRole {
        val id = getActiveId()
        return getMembers().firstOrNull { it.id == id }?.role ?: TeamRole.Admin
    }

    private fun overrides(): Map<String, Set<TeamPermission>> {
        val raw = store.get(ROLES_KEY) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = HashMap<String, Set<TeamPermission>>()
            for (role in TeamRole.entries) {
                val array = obj.optJSONArray(role.id) ?: continue
                val set = HashSet<TeamPermission>()
                for (i in 0 until array.length()) {
                    TeamPermission.fromId(array.opt(i))?.let { set.add(it) }
                }
                result[role.id] = set
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun permissionsOf(role: TeamRole): Set<TeamPermission> {
        if (role == TeamRole.Admin) return TeamPermission.entries.toSet()
        val chosen = overrides()[role.id] ?: defaultPermissions(role)
        return chosen - TeamPermission.Settings
    }

    fun setPermission(role: TeamRole, permission: TeamPermission, enabled: Boolean): Boolean {
        if (role == TeamRole.Admin || permission == TeamPermission.Settings) return false
        val current = permissionsOf(role)
        val updated = if (enabled) current + permission else current - permission
        val all = overrides().toMutableMap()
        all[role.id] = updated
        val obj = JSONObject()
        for ((id, set) in all) {
            val array = JSONArray()
            for (p in set) array.put(p.id)
            obj.put(id, array)
        }
        return store.set(ROLES_KEY, obj.toString())
    }

    /** L'utilisateur actif a-t-il ce droit ? */
    fun can(permission: TeamPermission): Boolean {
        val role = getActiveRole()
        return if (permission == TeamPermission.Settings) {
            role == TeamRole.Admin
        } else {
            permissionsOf(role).contains(permission)
        }
    }
}
