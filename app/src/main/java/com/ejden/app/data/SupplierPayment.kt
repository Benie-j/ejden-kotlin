package com.ejden.app.data

import org.json.JSONObject
import java.time.Instant

const val SUPPLIER_PAYMENTS_KEY = "ejden_supplier_payments"

data class SupplierPayment(
    val id: String,
    /** Les paiements créés ensemble (un versement) partagent le même lot. */
    val batchId: String,
    val purchaseId: String,
    val supplierId: String,
    val amount: Double,
    val method: PaymentMethod,
    val createdAt: String,
    val cancelledAt: String? = null
) {
    val isCancelled: Boolean get() = !cancelledAt.isNullOrEmpty()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("batchId", batchId)
        put("purchaseId", purchaseId)
        put("supplierId", supplierId)
        put("amount", amount)
        put("method", method.id)
        put("createdAt", createdAt)
        if (cancelledAt != null) put("cancelledAt", cancelledAt)
    }
}

private fun shortKey(value: Any?): String? =
    (value as? String)?.takeIf { it.isNotEmpty() && it.length <= 100 }

fun normalizeSupplierPayment(value: Any?): SupplierPayment? {
    val p = value as? JSONObject ?: return null
    val id = shortKey(p.opt("id"))
    val batchId = shortKey(p.opt("batchId"))
    val purchaseId = shortKey(p.opt("purchaseId"))
    val supplierId = shortKey(p.opt("supplierId"))
    val method = PaymentMethod.fromId(p.opt("method"))
    val amount = cleanAmount(p.opt("amount"))
    val createdAt = validDate(p.opt("createdAt"))

    if (id == null || batchId == null || purchaseId == null || supplierId == null ||
        method == null || method !in CREDIT_PAYMENT_METHODS ||
        amount == null || createdAt == null
    ) {
        return null
    }

    val cancelled = (p.opt("cancelledAt") as? String)?.takeIf { validDate(it) != null }
    return SupplierPayment(id, batchId, purchaseId, supplierId, amount, method, createdAt, cancelled)
}

fun readSupplierPayments(store: KeyValueStore): List<SupplierPayment> =
    readList(store, SUPPLIER_PAYMENTS_KEY, ::normalizeSupplierPayment)

/** Reste dû par achat (achats non annulés seulement). Clé = id de l'achat. */
fun computePurchaseRemainingMap(
    purchases: List<Purchase>,
    payments: List<SupplierPayment>
): Map<String, Double> {
    val paid = HashMap<String, Double>()
    for (payment in payments) {
        if (!payment.isCancelled) {
            paid[payment.purchaseId] = (paid[payment.purchaseId] ?: 0.0) + payment.amount
        }
    }

    val remaining = LinkedHashMap<String, Double>()
    for (purchase in purchases) {
        if (!purchase.isCancelled) {
            remaining[purchase.id] =
                maxOf(0.0, purchase.total - purchase.paid - (paid[purchase.id] ?: 0.0))
        }
    }
    return remaining
}

data class OpenPurchase(val purchase: Purchase, val remaining: Double)

/** amount : total dû ; purchases : achats encore à payer, du plus ancien au plus récent. */
data class SupplierDebt(val amount: Double, val purchases: List<OpenPurchase>)

fun computeSupplierDebt(
    purchases: List<Purchase>,
    payments: List<SupplierPayment>,
    supplierId: String
): SupplierDebt {
    val remaining = computePurchaseRemainingMap(purchases, payments)
    val open = purchases
        .filter { it.supplierId == supplierId && (remaining[it.id] ?: 0.0) > 0.0 }
        .sortedBy { parseDateMillis(it.createdAt) ?: 0L }
        .map { OpenPurchase(it, remaining[it.id] ?: 0.0) }
    return SupplierDebt(open.sumOf { it.remaining }, open)
}

sealed class SupplierPaymentResult {
    data object Ok : SupplierPaymentResult()
    data object InvalidAmount : SupplierPaymentResult()
    data object InvalidMethod : SupplierPaymentResult()
    data object NoDebt : SupplierPaymentResult()
    /** debt : ce qui est réellement dû. */
    data class TooMuch(val debt: Double) : SupplierPaymentResult()
    data object StorageError : SupplierPaymentResult()
}

sealed class CancelSupplierPaymentResult {
    data object Ok : CancelSupplierPaymentResult()
    data object NotFound : CancelSupplierPaymentResult()
    data object AlreadyCancelled : CancelSupplierPaymentResult()
    data object StorageError : CancelSupplierPaymentResult()
}

data class SupplierPaymentBatch(
    val batchId: String,
    val amount: Double,
    val method: PaymentMethod,
    val createdAt: String,
    val cancelledAt: String?
)

class SupplierPaymentRepository(
    private val store: KeyValueStore,
    private val purchases: PurchaseRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    fun getSupplierPayments(): List<SupplierPayment> = readSupplierPayments(store)

    private fun save(payments: List<SupplierPayment>): Boolean =
        writeList(store, SUPPLIER_PAYMENTS_KEY, payments.map { it.toJson() })

    fun getPurchaseRemainingMap(): Map<String, Double> =
        computePurchaseRemainingMap(purchases.getPurchases(), getSupplierPayments())

    fun getSupplierDebt(supplierId: String): SupplierDebt =
        computeSupplierDebt(purchases.getPurchases(), getSupplierPayments(), supplierId)

    /** Total dû à tous les fournisseurs. */
    fun getSuppliersDebtTotal(): Double = getPurchaseRemainingMap().values.sum()

    /**
     * Paiement au fournisseur, appliqué d'abord à ses achats les plus anciens.
     * Écrit en une seule fois : tout ou rien.
     */
    fun recordSupplierPayment(
        supplierId: String,
        amount: Double,
        method: PaymentMethod
    ): SupplierPaymentResult {
        val clean = cleanAmount(amount) ?: return SupplierPaymentResult.InvalidAmount
        if (method !in CREDIT_PAYMENT_METHODS) return SupplierPaymentResult.InvalidMethod

        val existing = getSupplierPayments()
        val debt = computeSupplierDebt(purchases.getPurchases(), existing, supplierId)
        if (debt.amount <= 0.0) return SupplierPaymentResult.NoDebt
        if (clean > debt.amount) return SupplierPaymentResult.TooMuch(debt.amount)

        val batchId = newId()
        val createdAt = toIso(now())
        val created = ArrayList<SupplierPayment>()
        var left = clean

        for (entry in debt.purchases) {
            if (left <= 0.0) break
            val part = minOf(left, entry.remaining)
            created.add(
                SupplierPayment(newId(), batchId, entry.purchase.id, supplierId, part, method, createdAt)
            )
            left -= part
        }

        return if (save(existing + created)) SupplierPaymentResult.Ok else SupplierPaymentResult.StorageError
    }

    /** Versements d'un fournisseur (regroupés), du plus récent au plus ancien. */
    fun getSupplierPaymentBatches(supplierId: String): List<SupplierPaymentBatch> {
        val batches = LinkedHashMap<String, SupplierPaymentBatch>()
        for (payment in getSupplierPayments()) {
            if (payment.supplierId != supplierId) continue
            val current = batches[payment.batchId]
            batches[payment.batchId] = if (current != null) {
                current.copy(amount = current.amount + payment.amount)
            } else {
                SupplierPaymentBatch(
                    payment.batchId, payment.amount, payment.method, payment.createdAt, payment.cancelledAt
                )
            }
        }
        return batches.values.sortedByDescending { parseDateMillis(it.createdAt) ?: 0L }
    }

    /** Annule un versement entier : la dette remonte, la caisse est corrigée. */
    fun cancelSupplierPayment(batchId: String): CancelSupplierPaymentResult {
        val payments = getSupplierPayments()
        val batch = payments.filter { it.batchId == batchId }
        if (batch.isEmpty()) return CancelSupplierPaymentResult.NotFound
        if (batch.all { it.isCancelled }) return CancelSupplierPaymentResult.AlreadyCancelled

        val stamp = toIso(now())
        val updated = payments.map {
            if (it.batchId == batchId && !it.isCancelled) it.copy(cancelledAt = stamp) else it
        }
        return if (save(updated)) CancelSupplierPaymentResult.Ok else CancelSupplierPaymentResult.StorageError
    }
}
