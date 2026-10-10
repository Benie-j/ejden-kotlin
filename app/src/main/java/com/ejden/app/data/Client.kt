package com.ejden.app.data

import org.json.JSONObject

data class Client(
    val id: String,
    val name: String,
    val phone: String,
    val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("phone", phone)
        put("createdAt", createdAt)
    }
}

fun normalizeClient(value: Any?): Client? {
    val c = value as? JSONObject ?: return null
    val name = cleanLabel(c.opt("name"), ModuleLimits.TEXT_MAX)
    val phone = cleanLabel(c.opt("phone"), ModuleLimits.PHONE_MAX)
    val createdAt = validDate(c.opt("createdAt"))
    val id = c.opt("id") as? String

    if (id == null || id.isEmpty() || id.length > 100 ||
        name.isEmpty() || createdAt == null || !isValidPhone(phone)
    ) {
        return null
    }
    return Client(id, name, phone, createdAt)
}

data class ClientDebt(val amount: Double, val sales: List<Sale>)

class ClientRepository(
    private val store: KeyValueStore,
    private val sales: SaleRepository,
    private val credits: CreditPaymentRepository
) {
    companion object {
        const val KEY = "ejden_clients"
    }

    fun getClients(): List<Client> = readList(store, KEY, ::normalizeClient)

    private fun save(clients: List<Client>): Boolean =
        writeList(store, KEY, clients.map { it.toJson() })

    fun getClientById(clientId: String): Client? =
        getClients().firstOrNull { it.id == clientId }

    /** Deux clients ne peuvent pas porter exactement le même nom. */
    fun clientNameExists(name: String, exceptId: String? = null): Boolean {
        val wanted = name.jsTrim().lowercase()
        return getClients().any { it.id != exceptId && it.name.lowercase() == wanted }
    }

    fun addClient(client: Client): Boolean {
        val clean = normalizeClient(client.toJson()) ?: return false
        return save(getClients() + clean)
    }

    fun updateClient(client: Client): Boolean {
        val clean = normalizeClient(client.toJson()) ?: return false
        val clients = getClients().toMutableList()
        val index = clients.indexOfFirst { it.id == clean.id }
        if (index == -1) return false
        clients[index] = clean
        return save(clients)
    }

    /** Supprime la fiche. Les ventes déjà faites ne sont pas touchées. */
    fun deleteClient(clientId: String): Boolean {
        val clients = getClients()
        val remaining = clients.filter { it.id != clientId }
        if (remaining.size == clients.size) return false
        return save(remaining)
    }

    /** Ventes non annulées avec un reste à payer, reliées par id client ou par nom du débiteur. */
    fun getClientDebt(client: Client): ClientDebt {
        val name = client.name.lowercase()
        val allSales = sales.getSales()
        val remaining = computeRemainingMap(allSales, credits.getCreditPayments())

        val debtSales = allSales
            .filter { sale ->
                (remaining[sale.id] ?: 0.0) > 0.0 &&
                    (sale.customerId == client.id ||
                        (sale.borrowerName != null && sale.borrowerName.jsTrim().lowercase() == name))
            }
            .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }

        return ClientDebt(debtSales.sumOf { remaining[it.id] ?: 0.0 }, debtSales)
    }

    /** Dernières régularisations d'un client (pour sa fiche), annulées exclues. */
    fun getClientPayments(client: Client, limit: Int = 5): List<CreditPayment> {
        val name = client.name.jsTrim().lowercase()
        val ids = sales.getSales()
            .filter { sale ->
                sale.customerId == client.id ||
                    (sale.borrowerName != null && sale.borrowerName.jsTrim().lowercase() == name)
            }
            .map { it.id }
            .toSet()

        return credits.getCreditPayments()
            .filter { it.saleId in ids && !it.isCancelled }
            .sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
            .take(maxOf(0, limit))
    }
}
