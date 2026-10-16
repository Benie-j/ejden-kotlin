package com.ejden.app.data

import org.json.JSONObject

data class Supplier(
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

fun normalizeSupplier(value: Any?): Supplier? {
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
    return Supplier(id, name, phone, createdAt)
}

class SupplierRepository(private val store: KeyValueStore) {
    companion object {
        const val KEY = "ejden_suppliers"
    }

    fun getSuppliers(): List<Supplier> = readList(store, KEY, ::normalizeSupplier)

    private fun save(suppliers: List<Supplier>): Boolean =
        writeList(store, KEY, suppliers.map { it.toJson() })

    fun getSupplierById(supplierId: String): Supplier? =
        getSuppliers().firstOrNull { it.id == supplierId }

    fun supplierNameExists(name: String, exceptId: String? = null): Boolean {
        val wanted = name.jsTrim().lowercase()
        return getSuppliers().any { it.id != exceptId && it.name.lowercase() == wanted }
    }

    fun addSupplier(supplier: Supplier): Boolean {
        val clean = normalizeSupplier(supplier.toJson()) ?: return false
        return save(getSuppliers() + clean)
    }

    fun updateSupplier(supplier: Supplier): Boolean {
        val clean = normalizeSupplier(supplier.toJson()) ?: return false
        val suppliers = getSuppliers().toMutableList()
        val index = suppliers.indexOfFirst { it.id == clean.id }
        if (index == -1) return false
        suppliers[index] = clean
        return save(suppliers)
    }

    // La suppression (refusee si une dette existe) arrive a l'etape 3.
}
