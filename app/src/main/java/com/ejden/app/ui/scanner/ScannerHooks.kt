package com.ejden.app.ui.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import com.ejden.app.data.CartLine
import com.ejden.app.data.CartResult
import com.ejden.app.data.JS_WHITESPACE_RUN
import com.ejden.app.data.Product
import com.ejden.app.data.addToCart
import com.ejden.app.data.jsTrim

private fun normalize(value: String): String = value.replace(JS_WHITESPACE_RUN, "").jsTrim()

/** Page Produits : le code scanné est placé dans la recherche. */
@Composable
fun rememberSearchScanner(
    products: () -> List<Product>,
    onCode: (String) -> Unit,
    notify: (String) -> Unit
): BarcodeScannerState = rememberBarcodeScanner(onFailure = notify) { code, scanner ->
    onCode(code)
    scanner.close()
    val found = products().any { it.barcode == code }
    notify(if (found) "Produit trouvé." else "Aucun produit avec ce code-barres.")
}

/** Formulaires produit : un code déjà utilisé par un AUTRE produit est refusé. */
@Composable
fun rememberFormScanner(
    products: List<Product>,
    currentId: String?,
    onCode: (String) -> Unit,
    notify: (String) -> Unit
): BarcodeScannerState = rememberBarcodeScanner(onFailure = notify) { code, scanner ->
    val existing = products.firstOrNull { p ->
        val barcode = p.barcode
        barcode != null && normalize(barcode) == code
    }
    if (existing != null && existing.id != currentId) {
        scanner.flash("Ce code est déjà utilisé par « ${existing.name} ».", ScanTone.Error, 2500L)
    } else {
        onCode(code)
        scanner.close()
        notify("Code scanné : $code")
    }
}

/** Nouvelle vente : chaque code scanné ajoute le produit au panier. */
private fun addScanned(
    rawCode: String,
    readProducts: () -> List<Product>,
    cart: () -> List<CartLine>,
    onProducts: (List<Product>) -> Unit,
    onCart: (CartResult) -> Unit,
    scanner: BarcodeScannerState
): Boolean {
    val code = normalize(rawCode)
    if (code.isEmpty()) return false

    val latest = readProducts()
    onProducts(latest)

    val product = latest.firstOrNull { p ->
        val barcode = p.barcode
        barcode != null && normalize(barcode) == code
    }
    if (product == null) {
        scanner.flash("Code inconnu : $code", ScanTone.Error)
        return false
    }
    if (product.stock <= 0) {
        scanner.flash("${product.name} : rupture de stock.", ScanTone.Error)
        return false
    }
    val inCart = cart().firstOrNull { it.product.id == product.id }?.quantity ?: 0
    if (inCart >= product.stock) {
        scanner.flash("${product.name} : stock disponible atteint.", ScanTone.Error)
        return false
    }

    onCart(addToCart(cart(), latest, product.id))
    scanner.flash("${product.name} ajouté", ScanTone.Success)
    return true
}

@Composable
fun rememberSalesScanner(
    readProducts: () -> List<Product>,
    cart: () -> List<CartLine>,
    onProducts: (List<Product>) -> Unit,
    onCart: (CartResult) -> Unit,
    notify: (String) -> Unit
): BarcodeScannerState {
    val state = rememberBarcodeScanner(onFailure = notify) { code, scanner ->
        addScanned(code, readProducts, cart, onProducts, onCart, scanner)
    }
    SideEffect {
        state.onManual = { raw -> addScanned(raw, readProducts, cart, onProducts, onCart, state) }
    }
    return state
}
