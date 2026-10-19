package com.ejden.app.data

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

enum class NotifCategory(val id: String, val label: String, val description: String) {
    Stock("stock", "Stock", "Ruptures et stock faible"),
    Credits("credits", "Crédits clients", "Impayés de plus de $CREDIT_OVERDUE_DAYS jours"),
    Orders("orders", "Commandes", "Livraisons prévues dépassées"),
    Suppliers("suppliers", "Dettes fournisseurs", "Achats restant à payer"),
    Invoices("invoices", "Factures", "Échues ou à échéance proche"),
    Quotes("quotes", "Devis", "Devis envoyés bientôt expirés")
}

enum class NotifSeverity(val label: String, val order: Int) {
    Danger("Urgent", 0),
    Warning("À surveiller", 1),
    Info("Info", 2)
}

data class AppNotification(
    val id: String,
    val category: NotifCategory,
    val severity: NotifSeverity,
    val title: String,
    val message: String,
    /** Route de destination (ex. « module/stock?filter=out »). */
    val route: String,
    val read: Boolean = false
)

private const val NOTIF_ID_MAX = 200
private const val NOTIF_STATE_MAX = 1000
private const val NOTIF_SOON_DAYS = 3

private fun notifMoney(v: Double) = "${formatNumber(v)} FCFA"
private fun notifDays(n: Int) = "$n jour${if (n > 1) "s" else ""}"
private fun notifDate(day: String): String {
    val d = LocalDate.parse(day)
    val month = d.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.FRENCH)
    return "${d.dayOfMonth} $month"
}
private fun plural(n: Int, word: String) = "$n $word${if (n > 1) "s" else ""}"
private fun invoiceRoute(id: String) = "module/factures?open=" + android.net.Uri.encode(id)

class NotificationRepository(
    private val store: KeyValueStore,
    private val products: ProductRepository,
    private val sales: SaleRepository,
    private val credits: CreditPaymentRepository,
    private val clients: ClientRepository,
    private val orders: OrderRepository,
    private val suppliers: SupplierRepository,
    private val supplierPayments: SupplierPaymentRepository,
    private val invoices: InvoiceRepository,
    private val quotes: QuoteRepository
) {
    companion object {
        const val SETTINGS_KEY = "ejden_notif_settings"
        const val STATE_KEY = "ejden_notif_state"
    }

    // ---------- Réglages ----------

    fun getSettings(): Map<NotifCategory, Boolean> {
        val result = NotifCategory.entries.associateWith { true }.toMutableMap()
        try {
            val raw = store.get(SETTINGS_KEY)
            val parsed = if (raw.isNullOrEmpty()) null else JSONTokener(raw).nextValue()
            if (parsed is JSONObject) {
                for (c in NotifCategory.entries) {
                    val v = parsed.opt(c.id)
                    if (v is Boolean) result[c] = v
                }
            }
        } catch (e: Exception) {
            // réglages illisibles : valeurs par défaut
        }
        return result
    }

    fun saveSettings(settings: Map<NotifCategory, Boolean>): Boolean {
        val json = JSONObject()
        for (c in NotifCategory.entries) json.put(c.id, settings[c] == true)
        return store.set(SETTINGS_KEY, json.toString())
    }

    // ---------- État (lu / effacé) ----------

    private class State(val read: List<String>, val dismissed: List<String>)

    private fun idsFrom(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until array.length()) {
            val s = array.opt(i)
            if (s is String && s.isNotEmpty() && s.length <= NOTIF_ID_MAX) out.add(s)
        }
        return out.take(NOTIF_STATE_MAX)
    }

    private fun cleanIds(list: List<String>): List<String> =
        list.filter { it.isNotEmpty() && it.length <= NOTIF_ID_MAX }.take(NOTIF_STATE_MAX)

    private fun getState(): State = try {
        val raw = store.get(STATE_KEY)
        val parsed = if (raw.isNullOrEmpty()) null else JSONTokener(raw).nextValue()
        if (parsed is JSONObject) {
            State(idsFrom(parsed.optJSONArray("read")), idsFrom(parsed.optJSONArray("dismissed")))
        } else State(emptyList(), emptyList())
    } catch (e: Exception) {
        State(emptyList(), emptyList())
    }

    private fun saveState(read: List<String>, dismissed: List<String>): Boolean {
        val json = JSONObject()
        json.put("read", JSONArray(cleanIds(read)))
        json.put("dismissed", JSONArray(cleanIds(dismissed)))
        return store.set(STATE_KEY, json.toString())
    }

    fun markRead(ids: List<String>): Boolean {
        val s = getState()
        return saveState((s.read + cleanIds(ids)).distinct(), s.dismissed)
    }

    fun dismiss(ids: List<String>): Boolean {
        val s = getState()
        return saveState(s.read, (s.dismissed + cleanIds(ids)).distinct())
    }

    fun restoreDismissed(): Boolean {
        val s = getState()
        return saveState(s.read, emptyList())
    }

    // ---------- Calcul des alertes ----------

    private fun collect(now: Instant): List<AppNotification> {
        val list = ArrayList<AppNotification>()

        for (p in products.getProducts()) {
            if (p.stock <= 0) {
                list.add(
                    AppNotification(
                        "stock-out:${p.id}", NotifCategory.Stock, NotifSeverity.Danger,
                        "Rupture : ${p.name}",
                        "Plus aucune unité en stock. Pensez à réapprovisionner.",
                        "module/stock?filter=out"
                    )
                )
            } else if (p.stockThreshold > 0 && p.stock <= p.stockThreshold) {
                list.add(
                    AppNotification(
                        "stock-low:${p.id}:${p.stock}", NotifCategory.Stock, NotifSeverity.Warning,
                        "Stock faible : ${p.name}",
                        "Il reste ${plural(p.stock, "unité")} (seuil d'alerte : ${p.stockThreshold}).",
                        "module/stock?filter=low"
                    )
                )
            }
        }

        val summary = computeCreditsSummary(sales.getSales(), credits.getCreditPayments(), clients.getClients(), now)
        for (g in summary.groups) {
            if (!g.overdue) continue
            list.add(
                AppNotification(
                    "credit:${g.key}:${g.amount.toLong()}", NotifCategory.Credits, NotifSeverity.Danger,
                    "Crédit en retard : ${g.name}",
                    "${notifMoney(g.amount)} à encaisser, dette la plus ancienne depuis ${notifDays(g.oldestDays)}.",
                    "module/credits"
                )
            )
        }

        for (o in orders.getOrders()) {
            val expected = o.expectedDate ?: continue
            if (!isOrderOverdue(o, now)) continue
            val supplier = o.kind == OrderKind.Supplier
            list.add(
                AppNotification(
                    "order:${o.id}:${o.status.id}", NotifCategory.Orders, NotifSeverity.Warning,
                    "${if (supplier) "Réception" else "Livraison"} en retard : ${o.partyName}",
                    "Prévue le ${notifDate(expected)} — ${notifMoney(o.total)}.",
                    "module/commandes"
                )
            )
        }

        for (s in suppliers.getSuppliers()) {
            val debt = supplierPayments.getSupplierDebt(s.id)
            if (debt.amount <= 0) continue
            list.add(
                AppNotification(
                    "supplier:${s.id}:${debt.amount.toLong()}", NotifCategory.Suppliers, NotifSeverity.Warning,
                    "À payer : ${s.name}",
                    "${notifMoney(debt.amount)} dus sur ${plural(debt.purchases.size, "achat")}.",
                    "module/fournisseurs"
                )
            )
        }

        val today = invoices.today()
        val soon = addDaysToDay(today, NOTIF_SOON_DAYS)
        for (inv in invoices.getInvoices()) {
            if (inv.status != InvoiceStatus.Issued) continue
            val remaining = inv.remaining()
            if (remaining <= 0) continue
            val label = if (inv.clientName.isNotEmpty()) "${inv.number} — ${inv.clientName}" else inv.number
            val route = invoiceRoute(inv.id)
            if (inv.dueDate < today) {
                list.add(
                    AppNotification(
                        "invoice-late:${inv.id}:${remaining.toLong()}", NotifCategory.Invoices, NotifSeverity.Danger,
                        "Facture échue : $label",
                        "${notifMoney(remaining)} restant dû, échéance du ${notifDate(inv.dueDate)}.",
                        route
                    )
                )
            } else if (inv.dueDate <= soon) {
                list.add(
                    AppNotification(
                        "invoice-soon:${inv.id}:${remaining.toLong()}", NotifCategory.Invoices, NotifSeverity.Info,
                        "Facture bientôt due : $label",
                        "${notifMoney(remaining)} à encaisser avant le ${notifDate(inv.dueDate)}.",
                        route
                    )
                )
            }
        }

        for (q in quotes.getQuotes()) {
            if (q.status != QuoteStatus.Sent || q.validUntil < today || q.validUntil > soon) continue
            list.add(
                AppNotification(
                    "quote:${q.id}:${q.validUntil}", NotifCategory.Quotes, NotifSeverity.Info,
                    "Devis bientôt expiré : ${q.number}${if (q.clientName.isNotEmpty()) " — ${q.clientName}" else ""}",
                    "Valable jusqu'au ${notifDate(q.validUntil)}. Relancez le client.",
                    "module/devis"
                )
            )
        }
        return list
    }

    /** Alertes à afficher : types activés, non effacées, non lues d'abord puis les plus urgentes. */
    fun getNotifications(now: Instant = Instant.now()): List<AppNotification> {
        val all = collect(now)
        val settings = getSettings()
        val state = getState()
        val active = all.map { it.id }.toSet()
        val read = state.read.filter { it in active }
        val dismissed = state.dismissed.filter { it in active }
        if (read.size != state.read.size || dismissed.size != state.dismissed.size) saveState(read, dismissed)
        val readSet = read.toSet()
        val dismissedSet = dismissed.toSet()
        return all
            .filter { settings[it.category] == true && it.id !in dismissedSet }
            .map { it.copy(read = it.id in readSet) }
            .sortedWith(compareBy<AppNotification> { it.read }.thenBy { it.severity.order })
            .take(200)
    }

    fun unreadCount(): Int = getNotifications().count { !it.read }

    fun dismissedCount(now: Instant = Instant.now()): Int {
        val settings = getSettings()
        val dismissed = getState().dismissed.toSet()
        return collect(now).count { it.id in dismissed && settings[it.category] == true }
    }
}
