package com.ejden.app.data

import java.time.Instant
import kotlin.math.floor

sealed class CompleteOrderResult {
    /** linkId : la vente (commande client) ou l'achat (commande fournisseur) créé. */
    data class Ok(val linkId: String) : CompleteOrderResult()
    data object NotFound : CompleteOrderResult()
    data object NotAllowed : CompleteOrderResult()
    data object InvalidAmount : CompleteOrderResult()
    data object InvalidMethod : CompleteOrderResult()
    data object CreditNeedsClient : CompleteOrderResult()
    data class OutOfStock(val productName: String) : CompleteOrderResult()
    data class PurchaseRejected(val reason: PurchaseRejection) : CompleteOrderResult()
    data object StorageError : CompleteOrderResult()
}

/** Terminer une commande : la livrer (client) ou la réceptionner (fournisseur). */
class OrderCompletion(
    private val orders: OrderRepository,
    private val sales: SaleRepository,
    private val purchases: PurchaseRepository,
    private val products: ProductRepository,
    private val now: () -> Instant = { Instant.now() }
) {
    /**
     * Livre une commande client : crée la vente (stock diminué, argent en caisse).
     * Si le client paie moins que le total, le reste devient un crédit
     * (client enregistré obligatoire).
     */
    fun completeCustomerOrder(
        orderId: String,
        method: PaymentMethod,
        amountPaid: Double
    ): CompleteOrderResult {
        val order = orders.getOrderById(orderId) ?: return CompleteOrderResult.NotFound
        if (order.kind != OrderKind.Customer || !order.isOpen) return CompleteOrderResult.NotAllowed

        if (method !in CREDIT_PAYMENT_METHODS) return CompleteOrderResult.InvalidMethod

        if (!amountPaid.isFinite() || amountPaid != floor(amountPaid) ||
            amountPaid < 0.0 || amountPaid > order.total
        ) {
            return CompleteOrderResult.InvalidAmount
        }

        val onCredit = amountPaid < order.total
        if (onCredit && order.partyId == null) return CompleteOrderResult.CreditNeedsClient

        val known = products.getProducts()
        val items = order.items.map { item ->
            SaleItem(
                productId = item.productId,
                productName = item.productName,
                unitPrice = item.unitPrice,
                unitCost = known.firstOrNull { it.id == item.productId }?.purchasePrice,
                quantity = item.quantity.toDouble(),
                total = item.quantity * item.unitPrice
            )
        }

        val sale = Sale(
            id = newId(),
            createdAt = toIso(now()),
            items = items,
            subtotal = order.total,
            discount = 0.0,
            total = order.total,
            paymentMethod = if (onCredit) PaymentMethod.Credit else method,
            amountPaid = amountPaid,
            change = 0.0,
            remaining = order.total - amountPaid,
            customerId = order.partyId,
            borrowerName = if (onCredit) order.partyName else null
        )

        when (val result = sales.recordSale(sale)) {
            RecordSaleResult.Ok -> Unit
            is RecordSaleResult.OutOfStock -> return CompleteOrderResult.OutOfStock(result.productName)
            RecordSaleResult.StorageError -> return CompleteOrderResult.StorageError
        }

        if (!orders.markCompleted(order.id, sale.id)) {
            // Retour en arrière : la vente est annulée, le stock revient.
            sales.cancelSale(sale.id)
            return CompleteOrderResult.StorageError
        }
        return CompleteOrderResult.Ok(sale.id)
    }

    /**
     * Réceptionne une commande fournisseur : crée l'achat (stock augmenté, dette
     * ou paiement). Les prix de la commande deviennent les prix d'achat de la livraison.
     */
    fun receiveSupplierOrder(
        orderId: String,
        paid: Double,
        updatePrices: Boolean
    ): CompleteOrderResult {
        val order = orders.getOrderById(orderId) ?: return CompleteOrderResult.NotFound
        if (order.kind != OrderKind.Supplier || !order.isOpen) return CompleteOrderResult.NotAllowed

        val result = purchases.recordPurchase(
            PurchaseInput(
                supplierId = order.partyId ?: "",
                lines = order.items.map { PurchaseLine(it.productId, it.quantity, it.unitPrice) },
                paid = paid,
                note = "Commande reçue",
                updatePrices = updatePrices
            )
        )

        val purchase = when (result) {
            is RecordPurchaseResult.Rejected -> return CompleteOrderResult.PurchaseRejected(result.reason)
            is RecordPurchaseResult.Ok -> result.purchase
        }

        if (!orders.markCompleted(order.id, purchase.id)) {
            // Retour en arrière : l'achat est annulé, le stock revient.
            purchases.cancelPurchase(purchase.id)
            return CompleteOrderResult.StorageError
        }
        return CompleteOrderResult.Ok(purchase.id)
    }
}
