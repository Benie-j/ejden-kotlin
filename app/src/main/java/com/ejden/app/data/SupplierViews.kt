package com.ejden.app.data

/** Versements d'un fournisseur regroupés par lot, du plus récent au plus ancien. */
fun groupSupplierPaymentBatches(
    payments: List<SupplierPayment>,
    supplierId: String
): List<SupplierPaymentBatch> {
    val batches = LinkedHashMap<String, SupplierPaymentBatch>()
    for (payment in payments) {
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
