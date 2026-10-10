package com.ejden.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val RIZ = Product("p1", "Riz", 600.0, 1000.0, 10, 0, null, null, null)
private val SEL = Product("p2", "Sel", 100.0, 200.0, 2, 0, null, null, null)
private val AWA = Client("c1", "Awa", "", "2026-10-01T10:00:00.000Z")

private class CheckoutEnv {
    val store = InMemoryStore()
    val products = ProductRepository(store)
    val sales = SaleRepository(store, products)
    val credits = CreditPaymentRepository(store, sales)
    val clients = ClientRepository(store, sales, credits)
    val checkout = SaleCheckout(sales, clients)
}

private fun ready(plan: CheckoutPlan): CheckoutPlan.Ready = plan as CheckoutPlan.Ready
private fun rejected(plan: CheckoutPlan): CheckoutPlan.Rejected = plan as CheckoutPlan.Rejected

private fun input(
    lines: List<CartLine> = listOf(CartLine(RIZ, 2)),
    discount: Double = 0.0,
    method: PaymentMethod = PaymentMethod.Cash,
    amount: String = "2000",
    choice: String = "",
    name: String = "",
    save: Boolean = true,
    clients: List<Client> = emptyList()
) = CheckoutInput(lines, discount, method, amount, choice, name, save, clients)

class SaleCheckoutTest {

    @Test
    fun ajoutAuPanierRespecteLeStock() {
        val products = listOf(RIZ, SEL, RIZ.copy(id = "p3", name = "Vide", stock = 0))

        val first = addToCart(emptyList(), products, "p1")
        assertEquals("Riz ajouté au panier.", first.message)
        assertEquals(1, first.lines.size)

        assertEquals("Produit introuvable.", addToCart(emptyList(), products, "zz").message)
        assertEquals("Ce produit est en rupture de stock.", addToCart(emptyList(), products, "p3").message)

        var lines = addToCart(emptyList(), products, "p2").lines
        lines = addToCart(lines, products, "p2").lines
        assertEquals(2, lines.single().quantity)
        val third = addToCart(lines, products, "p2")
        assertEquals("Stock disponible atteint.", third.message)
        assertEquals(2, third.lines.single().quantity)
    }

    @Test
    fun quantitesEtSuppression() {
        val lines = listOf(CartLine(RIZ, 1))

        val more = changeCartQuantity(lines, "p1", 1)
        assertNull(more.message)
        assertEquals(2, more.lines.single().quantity)

        assertEquals("Stock disponible atteint.", changeCartQuantity(lines, "p1", 100).message)

        val gone = changeCartQuantity(more.lines, "p1", -2)
        assertEquals("Riz retiré du panier.", gone.message)
        assertTrue(gone.lines.isEmpty())

        assertNull(removeFromCart(lines, "inconnu").message)
    }

    @Test
    fun panierRelu() {
        val lines = listOf(CartLine(RIZ, 5))

        val short = refreshCart(lines, listOf(RIZ.copy(stock = 3))) as CartRefresh.Failed
        assertEquals("Stock insuffisant pour « Riz ».", short.message)

        val missing = refreshCart(lines, emptyList()) as CartRefresh.Failed
        assertEquals("« Riz » n'existe plus.", missing.message)

        val ok = refreshCart(lines, listOf(RIZ.copy(stock = 8, salePrice = 1200.0))) as CartRefresh.Ok
        assertEquals(1200.0, ok.lines.single().product.salePrice, 0.0)
        assertEquals(5, ok.lines.single().quantity)
    }

    @Test
    fun totauxEtRemise() {
        val lines = listOf(CartLine(RIZ, 2))
        assertEquals(2000.0, cartSubtotal(lines), 0.0)
        assertEquals(2, cartQuantity(lines))
        assertEquals(1500.0, cartTotal(lines, 500.0), 0.0)
        assertEquals(0.0, cartTotal(lines, 5000.0), 0.0)
        assertEquals(2000.0, clampDiscount(5000.0, 2000.0), 0.0)

        assertEquals(DiscountResult.Ok(0.0), parseDiscount("", 1000.0))
        assertEquals(DiscountResult.Ok(500.0), parseDiscount(" 500 ", 1000.0))
        assertEquals(DiscountResult.Ok(251.0), parseDiscount("250,6", 1000.0))
        assertEquals(DiscountResult.Invalid("Montant de remise invalide."), parseDiscount("abc", 1000.0))
        assertEquals(DiscountResult.Invalid("Montant de remise invalide."), parseDiscount("-5", 1000.0))
        assertEquals(
            DiscountResult.Invalid("La remise ne peut pas dépasser le sous-total."),
            parseDiscount("1500", 1000.0)
        )
    }

    @Test
    fun calculDePaiement() {
        val cash = calcPayment(1500.0, "2000", PaymentMethod.Cash)
        assertEquals(500.0, cash.change, 0.0)
        assertEquals(0.0, cash.remaining, 0.0)
        assertTrue(cash.canConfirm)

        val short = calcPayment(1500.0, "1000", PaymentMethod.Cash)
        assertEquals(500.0, short.remaining, 0.0)
        assertFalse(short.canConfirm)

        val credit = calcPayment(1500.0, "0", PaymentMethod.Credit)
        assertEquals(1500.0, credit.remaining, 0.0)
        assertEquals(0.0, credit.change, 0.0)
        assertTrue(credit.canConfirm)
        assertFalse(calcPayment(1500.0, "2000", PaymentMethod.Credit).canConfirm)

        assertEquals(0.0, calcPayment(1500.0, "", PaymentMethod.Cash).paid, 0.0)
        assertEquals(0.0, calcPayment(1500.0, "abc", PaymentMethod.Cash).paid, 0.0)
        assertFalse(calcPayment(0.0, "0", PaymentMethod.Cash).canConfirm)
    }

    @Test
    fun venteEnEspeces() {
        val plan = ready(planCheckout(input(amount = "2500")))
        val sale = plan.sale
        assertEquals(2000.0, sale.subtotal, 0.0)
        assertEquals(2000.0, sale.total, 0.0)
        assertEquals(2500.0, sale.amountPaid, 0.0)
        assertEquals(500.0, sale.change, 0.0)
        assertEquals(0.0, sale.remaining, 0.0)
        assertNull(sale.customerId)
        assertNull(sale.borrowerName)
        assertNull(plan.clientToCreate)
        assertEquals(600.0, sale.items.single().unitCost!!, 0.0)
        assertEquals(2.0, sale.items.single().quantity, 0.0)
        assertEquals(2000.0, sale.items.single().total, 0.0)
    }

    @Test
    fun refusDeLEncaissement() {
        assertEquals("Le panier est vide.", rejected(planCheckout(input(lines = emptyList()))).message)
        assertEquals(
            "Le montant de la vente est invalide.",
            rejected(planCheckout(input(discount = 2000.0))).message
        )

        val short = rejected(planCheckout(input(amount = "1000")))
        assertEquals("Le montant payé est insuffisant.", short.message)
        assertEquals(CheckoutField.Amount, short.field)
    }

    @Test
    fun refusDeLaVenteACredit() {
        val credit = PaymentMethod.Credit

        val noClient = rejected(planCheckout(input(method = credit, amount = "0", choice = "")))
        assertEquals("Choisissez un client.", noClient.message)
        assertEquals(CheckoutField.Client, noClient.field)

        val noName = rejected(planCheckout(input(method = credit, amount = "0", choice = NEW_CLIENT, name = "  ")))
        assertEquals("Indiquez le nom du client.", noName.message)
        assertEquals(CheckoutField.Name, noName.field)

        val tooLong = rejected(
            planCheckout(input(method = credit, amount = "0", choice = NEW_CLIENT, name = "x".repeat(81)))
        )
        assertEquals("Le nom est trop long (80 caractères max).", tooLong.message)

        val tooMuch = rejected(
            planCheckout(input(method = credit, amount = "3000", choice = NEW_CLIENT, name = "Bob"))
        )
        assertEquals("Le montant versé ne peut pas dépasser le total.", tooMuch.message)
    }

    @Test
    fun clientDuCredit() {
        val credit = PaymentMethod.Credit

        val chosen = ready(planCheckout(input(method = credit, amount = "500", choice = "c1", clients = listOf(AWA))))
        assertEquals("c1", chosen.sale.customerId)
        assertEquals("Awa", chosen.sale.borrowerName)
        assertEquals(1500.0, chosen.sale.remaining, 0.0)
        assertEquals(0.0, chosen.sale.change, 0.0)
        assertNull(chosen.clientToCreate)

        // Même nom (casse différente) : on réutilise la fiche existante.
        val same = ready(
            planCheckout(input(method = credit, amount = "0", choice = NEW_CLIENT, name = "AWA", clients = listOf(AWA)))
        )
        assertEquals("c1", same.sale.customerId)
        assertEquals("AWA", same.sale.borrowerName)
        assertNull(same.clientToCreate)

        // Nouveau client enregistré.
        val created = ready(
            planCheckout(input(method = credit, amount = "0", choice = NEW_CLIENT, name = "  Bob   Dupont ", save = true))
        )
        assertEquals("Bob Dupont", created.clientToCreate!!.name)
        assertEquals(created.clientToCreate!!.id, created.sale.customerId)
        assertEquals("Bob Dupont", created.sale.borrowerName)

        // Nouveau client non enregistré.
        val unsaved = ready(
            planCheckout(input(method = credit, amount = "0", choice = NEW_CLIENT, name = "Bob", save = false))
        )
        assertNull(unsaved.clientToCreate)
        assertNull(unsaved.sale.customerId)
        assertEquals("Bob", unsaved.sale.borrowerName)
    }

    @Test
    fun enregistrementDeLaVente() {
        val env = CheckoutEnv()
        env.products.addProduct(RIZ)

        val plan = ready(
            planCheckout(input(method = PaymentMethod.Credit, amount = "500", choice = NEW_CLIENT, name = "Bob"))
        )
        val result = env.checkout.submit(plan) as SubmitResult.Ok
        assertEquals(2000.0, result.total, 0.0)
        assertNull(result.warning)

        assertEquals(8, env.products.getProductById("p1")!!.stock)
        assertEquals(1, env.sales.getSales().size)
        assertNotNull(env.clients.getClients().firstOrNull { it.name == "Bob" })
    }

    @Test
    fun stockInsuffisantALEnregistrement() {
        val env = CheckoutEnv()
        env.products.addProduct(RIZ.copy(stock = 1))

        val plan = ready(planCheckout(input()))
        assertEquals(SubmitResult.OutOfStock("Riz"), env.checkout.submit(plan))
        assertTrue(env.sales.getSales().isEmpty())
    }

    @Test
    fun dettesDesClientsIdentiquesAuCalculParClient() {
        val env = CheckoutEnv()
        val bob = Client("c2", "Bob", "", "2026-10-01T10:00:00.000Z")
        env.clients.addClient(AWA)
        env.clients.addClient(bob)

        fun creditSale(id: String, remaining: Double, customer: String?, borrower: String?) = Sale(
            id, "2026-10-05T10:00:00.000Z", listOf(SaleItem("p", "P", remaining, null, 1.0, remaining)),
            remaining, 0.0, remaining, PaymentMethod.Credit, 0.0, 0.0, remaining, customer, borrower
        )

        env.sales.saveSales(
            listOf(
                creditSale("s1", 1000.0, "c1", null),
                creditSale("s2", 500.0, null, "  awa "),
                creditSale("s3", 300.0, "c2", "Bob")
            )
        )
        env.credits.recordCreditPayment(listOf("s1"), 200.0, PaymentMethod.Cash)

        val debts = clientDebts(
            env.clients.getClients(), env.sales.getSales(), env.credits.getCreditPayments()
        )
        assertEquals(env.clients.getClientDebt(AWA).amount, debts["c1"]!!, 0.0)
        assertEquals(env.clients.getClientDebt(bob).amount, debts["c2"]!!, 0.0)
        assertEquals(1300.0, debts["c1"]!!, 0.0)
        assertEquals(300.0, debts["c2"]!!, 0.0)
    }
}
