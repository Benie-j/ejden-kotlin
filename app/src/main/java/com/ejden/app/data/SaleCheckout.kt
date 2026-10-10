package com.ejden.app.data

import java.time.Instant

fun formatMoney(value: Double): String = formatNumber(value) + " FCFA"

/** Montant court d'une dette : « 12 500 F ». */
fun formatDebt(value: Double): String = formatNumber(value) + " F"

// ---------------------------------------------------------------------------
// Panier
// ---------------------------------------------------------------------------

data class CartLine(val product: Product, val quantity: Int)

/** lines : nouveau panier ; message : texte à afficher (ou null). */
data class CartResult(val lines: List<CartLine>, val message: String?)

fun cartSubtotal(lines: List<CartLine>): Double = lines.sumOf { it.product.salePrice * it.quantity }

fun cartQuantity(lines: List<CartLine>): Int = lines.sumOf { it.quantity }

fun cartTotal(lines: List<CartLine>, discount: Double): Double =
    maxOf(0.0, cartSubtotal(lines) - discount)

/** La remise ne peut jamais dépasser le sous-total. */
fun clampDiscount(discount: Double, subtotal: Double): Double =
    if (discount > subtotal) subtotal else discount

fun addToCart(lines: List<CartLine>, products: List<Product>, productId: String): CartResult {
    val product = products.firstOrNull { it.id == productId }
        ?: return CartResult(lines, "Produit introuvable.")
    if (product.stock <= 0) return CartResult(lines, "Ce produit est en rupture de stock.")

    val existing = lines.firstOrNull { it.product.id == productId }
    val updated = if (existing != null) {
        if (existing.quantity >= product.stock) return CartResult(lines, "Stock disponible atteint.")
        lines.map { if (it.product.id == productId) it.copy(quantity = it.quantity + 1) else it }
    } else {
        lines + CartLine(product, 1)
    }
    return CartResult(updated, "${product.name} ajouté au panier.")
}

fun changeCartQuantity(lines: List<CartLine>, productId: String, amount: Int): CartResult {
    val item = lines.firstOrNull { it.product.id == productId } ?: return CartResult(lines, null)
    val newQuantity = item.quantity + amount
    if (newQuantity <= 0) return removeFromCart(lines, productId)
    if (newQuantity > item.product.stock) return CartResult(lines, "Stock disponible atteint.")
    return CartResult(
        lines.map { if (it.product.id == productId) it.copy(quantity = newQuantity) else it },
        null
    )
}

fun removeFromCart(lines: List<CartLine>, productId: String): CartResult {
    val item = lines.firstOrNull { it.product.id == productId } ?: return CartResult(lines, null)
    return CartResult(lines.filter { it.product.id != productId }, "${item.product.name} retiré du panier.")
}

sealed class CartRefresh {
    data class Ok(val lines: List<CartLine>) : CartRefresh()
    data class Failed(val message: String) : CartRefresh()
}

/** Remet à jour les produits du panier avec le stock réel ; échoue si la vente n'est plus possible. */
fun refreshCart(lines: List<CartLine>, latest: List<Product>): CartRefresh {
    val refreshed = ArrayList<CartLine>()
    for (line in lines) {
        val product = latest.firstOrNull { it.id == line.product.id }
            ?: return CartRefresh.Failed("« ${line.product.name} » n'existe plus.")
        if (product.stock < line.quantity) {
            return CartRefresh.Failed("Stock insuffisant pour « ${product.name} ».")
        }
        refreshed.add(CartLine(product, line.quantity))
    }
    return CartRefresh.Ok(refreshed)
}

sealed class DiscountResult {
    data class Ok(val amount: Double) : DiscountResult()
    data class Invalid(val message: String) : DiscountResult()
}

fun parseDiscount(input: String, subtotal: Double): DiscountResult {
    val text = input.jsTrim().replace(",", ".")
    val value = if (text.isEmpty()) 0.0 else (text.toDoubleOrNull() ?: Double.NaN)
    if (!value.isFinite() || value < 0.0) return DiscountResult.Invalid("Montant de remise invalide.")
    if (value > subtotal) return DiscountResult.Invalid("La remise ne peut pas dépasser le sous-total.")
    return DiscountResult.Ok(Math.round(value).toDouble())
}

// ---------------------------------------------------------------------------
// Paiement
// ---------------------------------------------------------------------------

data class PaymentCalc(
    val paid: Double,
    val remaining: Double,
    val change: Double,
    val canConfirm: Boolean
)

fun calcPayment(total: Double, amountText: String, method: PaymentMethod): PaymentCalc {
    val text = amountText.jsTrim()
    val raw = if (text.isEmpty()) 0.0 else (text.toDoubleOrNull() ?: Double.NaN)
    val paid = if (raw.isFinite() && raw >= 0.0) Math.round(raw).toDouble() else 0.0

    val credit = method == PaymentMethod.Credit
    val remaining = maxOf(0.0, total - paid)
    val change = if (credit) 0.0 else maxOf(0.0, paid - total)
    // Paiement classique : le montant doit couvrir la vente.
    // Crédit : le montant versé ne doit pas dépasser le total.
    val valid = if (credit) paid <= total else paid >= total
    return PaymentCalc(paid, remaining, change, total > 0.0 && valid)
}

/** Ce que doit chaque client (id -> montant), calculé en une seule lecture. */
fun clientDebts(
    clients: List<Client>,
    sales: List<Sale>,
    payments: List<CreditPayment>
): Map<String, Double> {
    val remaining = computeRemainingMap(sales, payments)
    val result = HashMap<String, Double>()
    for (client in clients) {
        val name = client.name.lowercase()
        var amount = 0.0
        for (sale in sales) {
            val due = remaining[sale.id] ?: 0.0
            val borrower = sale.borrowerName
            val matches = sale.customerId == client.id ||
                (borrower != null && borrower.jsTrim().lowercase() == name)
            if (due > 0.0 && matches) amount += due
        }
        result[client.id] = amount
    }
    return result
}

// ---------------------------------------------------------------------------
// Validation de l'encaissement
// ---------------------------------------------------------------------------

const val NEW_CLIENT = "__new__"

enum class CheckoutField { Client, Name, Amount }

data class CheckoutInput(
    val lines: List<CartLine>,
    val discount: Double,
    val method: PaymentMethod,
    val amountText: String,
    /** "" : rien choisi, NEW_CLIENT : nouveau client, sinon identifiant d'un client. */
    val clientChoice: String,
    val typedName: String,
    val saveClient: Boolean,
    /** Tous les clients enregistrés (relus au moment de valider). */
    val clients: List<Client>,
    val now: Instant = Instant.now()
)

sealed class CheckoutPlan {
    data class Ready(val sale: Sale, val clientToCreate: Client?) : CheckoutPlan()
    data class Rejected(val message: String, val field: CheckoutField? = null) : CheckoutPlan()
}

fun planCheckout(input: CheckoutInput): CheckoutPlan {
    if (input.lines.isEmpty()) return CheckoutPlan.Rejected("Le panier est vide.")

    val subtotal = cartSubtotal(input.lines)
    val discount = clampDiscount(input.discount, subtotal)
    val total = cartTotal(input.lines, discount)
    if (total <= 0.0) return CheckoutPlan.Rejected("Le montant de la vente est invalide.")

    val credit = input.method == PaymentMethod.Credit
    val amountPaid = calcPayment(total, input.amountText, input.method).paid

    // Client du crédit : enregistré (choisi) ou nouveau (nom saisi).
    val chosen = if (!credit || input.clientChoice == "" || input.clientChoice == NEW_CLIENT) {
        null
    } else {
        input.clients.firstOrNull { it.id == input.clientChoice }
    }
    val typedName = if (credit) input.typedName.replace(JS_WHITESPACE_RUN, " ").jsTrim() else ""
    val borrower = chosen?.name ?: typedName

    if (credit) {
        if (chosen == null && typedName.isEmpty()) {
            return if (input.clientChoice == "") {
                CheckoutPlan.Rejected("Choisissez un client.", CheckoutField.Client)
            } else {
                CheckoutPlan.Rejected("Indiquez le nom du client.", CheckoutField.Name)
            }
        }
        if (borrower.length > ModuleLimits.TEXT_MAX) {
            return CheckoutPlan.Rejected(
                "Le nom est trop long (${ModuleLimits.TEXT_MAX} caractères max).", CheckoutField.Name
            )
        }
        if (amountPaid > total) {
            return CheckoutPlan.Rejected("Le montant versé ne peut pas dépasser le total.")
        }
    } else if (amountPaid < total) {
        return CheckoutPlan.Rejected("Le montant payé est insuffisant.", CheckoutField.Amount)
    }

    val items = input.lines.map { line ->
        SaleItem(
            productId = line.product.id,
            productName = line.product.name,
            unitPrice = line.product.salePrice,
            unitCost = line.product.purchasePrice,
            quantity = line.quantity.toDouble(),
            total = line.product.salePrice * line.quantity
        )
    }

    // Nouveau nom : lié à la fiche existante du même nom, sinon (case cochée)
    // une fiche sera créée APRÈS l'enregistrement réussi de la vente.
    var creditClientId: String? = chosen?.id
    var clientToCreate: Client? = null
    if (credit && chosen == null) {
        val sameName = input.clients.firstOrNull { it.name.lowercase() == typedName.lowercase() }
        if (sameName != null) {
            creditClientId = sameName.id
        } else if (input.saveClient) {
            clientToCreate = Client(newId(), typedName, "", toIso(input.now))
            creditClientId = clientToCreate.id
        }
    }

    val sale = Sale(
        id = newId(),
        createdAt = toIso(input.now),
        items = items,
        subtotal = subtotal,
        discount = discount,
        total = total,
        paymentMethod = input.method,
        amountPaid = amountPaid,
        change = if (credit) 0.0 else maxOf(0.0, amountPaid - total),
        remaining = maxOf(0.0, total - amountPaid),
        customerId = if (credit) creditClientId else null,
        borrowerName = if (credit) borrower else null
    )
    return CheckoutPlan.Ready(sale, clientToCreate)
}

sealed class SubmitResult {
    data class Ok(val total: Double, val warning: String?) : SubmitResult()
    data class OutOfStock(val productName: String) : SubmitResult()
    data object Failed : SubmitResult()
}

class SaleCheckout(
    private val sales: SaleRepository,
    private val clients: ClientRepository
) {
    /** Enregistre la vente (et baisse le stock), puis crée la fiche du nouveau client si demandé. */
    fun submit(plan: CheckoutPlan.Ready): SubmitResult {
        return when (val result = sales.recordSale(plan.sale)) {
            is RecordSaleResult.OutOfStock -> SubmitResult.OutOfStock(result.productName)
            RecordSaleResult.StorageError -> SubmitResult.Failed
            RecordSaleResult.Ok -> {
                val client = plan.clientToCreate
                val warning = if (client != null && !clients.addClient(client)) {
                    "Vente enregistrée, mais la fiche client n'a pas pu être créée."
                } else {
                    null
                }
                SubmitResult.Ok(plan.sale.total, warning)
            }
        }
    }
}
