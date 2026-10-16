package com.ejden.app.data

/**
 * Sorties de caisse dues aux achats : ce qui est payé à la livraison
 * et les versements aux fournisseurs. Achats et versements annulés exclus.
 */
fun purchaseCashMovements(
    purchases: List<Purchase>,
    payments: List<SupplierPayment>
): List<CashMovement> {
    val result = ArrayList<CashMovement>()
    val byId = purchases.associateBy { it.id }

    for (purchase in purchases) {
        if (!purchase.isCancelled && purchase.paid > 0.0) {
            result.add(
                CashMovement(
                    id = "purchase-" + purchase.id,
                    source = CashSource.Purchase,
                    amount = -purchase.paid,
                    label = "Achat · " + purchase.supplierName,
                    createdAt = purchase.createdAt
                )
            )
        }
    }

    for (payment in payments) {
        val purchase = byId[payment.purchaseId]
        if (payment.isCancelled || purchase == null || purchase.isCancelled) continue
        result.add(
            CashMovement(
                id = "supplier-payment-" + payment.id,
                source = CashSource.SupplierPayment,
                amount = -payment.amount,
                label = "Paiement fournisseur · " + purchase.supplierName,
                createdAt = payment.createdAt,
                method = payment.method
            )
        )
    }
    return result
}

/** Lignes de l'historique du stock : achats (entrées) et leurs annulations (sorties). */
fun purchaseStockHistory(purchases: List<Purchase>, productId: String?): List<StockHistoryEntry> {
    val entries = ArrayList<StockHistoryEntry>()

    for (purchase in purchases) {
        purchase.items.forEachIndexed { position, item ->
            if (productId != null && item.productId != productId) return@forEachIndexed

            entries.add(
                StockHistoryEntry(
                    "p-" + purchase.id + "-" + position, item.productId, item.productName,
                    item.quantity.toDouble(), "Achat · " + purchase.supplierName,
                    StockSource.Purchase, purchase.createdAt, null
                )
            )

            val cancelledAt = purchase.cancelledAt
            if (cancelledAt != null) {
                entries.add(
                    StockHistoryEntry(
                        "pc-" + purchase.id + "-" + position, item.productId, item.productName,
                        -item.quantity.toDouble(), "Annulation achat · " + purchase.supplierName,
                        StockSource.PurchaseCancel, cancelledAt, null
                    )
                )
            }
        }
    }
    return entries
}
