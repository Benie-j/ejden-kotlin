package com.ejden.app.data

import java.time.ZoneId

fun cashSourceLabel(source: CashSource): String = when (source) {
    CashSource.Sale -> "Vente"
    CashSource.CreditPayment -> "Remboursement de crédit"
    CashSource.Entry -> "Entrée de caisse"
    CashSource.Exit -> "Sortie de caisse"
    CashSource.Expense -> "Dépense"
    CashSource.Purchase -> "Achat fournisseur"
    CashSource.SupplierPayment -> "Paiement fournisseur"
    CashSource.InvoicePayment -> "Paiement de facture"
}

/** Mode de paiement vu depuis la caisse : le crédit y représente des acomptes. */
fun cashMethodLabel(method: PaymentMethod): String =
    if (method == PaymentMethod.Credit) "Acomptes sur crédit" else paymentLabel(method)

/** Deuxième ligne d'un mouvement : nature, mode de paiement (si utile) et heure. */
fun cashMovementSubtitle(movement: CashMovement, zone: ZoneId): String {
    val time = timeText(movement.createdAt, zone)
    val method = movement.method
    val withMethod = method != null && (
        movement.source == CashSource.Sale ||
            movement.source == CashSource.CreditPayment ||
            movement.source == CashSource.InvoicePayment
        )
    return if (method != null && withMethod) {
        cashSourceLabel(movement.source) + " · " + cashMethodLabel(method) + " · " + time
    } else {
        cashSourceLabel(movement.source) + " · " + time
    }
}
