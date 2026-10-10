package com.ejden.app.data

enum class ProductField { Name, Purchase, Sale, Stock, Threshold, Barcode }

/** Texte tel que saisi dans le formulaire. */
data class ProductFormInput(
    val name: String,
    val purchase: String,
    val sale: String,
    val stock: String,
    val threshold: String,
    val barcode: String,
    val category: String,
    val photo: String?
)

sealed class ProductFormResult {
    data class Valid(val product: Product) : ProductFormResult()
    data class Invalid(val message: String, val field: ProductField) : ProductFormResult()
}

private const val REQUIRED = "Veuillez renseigner ce champ."

/** Équivalent de Number(valeur || 0) : vide = 0, illisible = NaN. */
private fun formNumber(text: String): Double {
    val t = text.jsTrim()
    return if (t.isEmpty()) 0.0 else (t.toDoubleOrNull() ?: Double.NaN)
}

private fun isInteger(value: Double): Boolean = value.isFinite() && value == Math.floor(value)

/** Champs obligatoires, dans l'ordre de la page (le navigateur s'arrête au premier vide). */
private fun requiredCheck(input: ProductFormInput): ProductFormResult.Invalid? = when {
    input.name.isEmpty() -> ProductFormResult.Invalid(REQUIRED, ProductField.Name)
    input.purchase.isEmpty() -> ProductFormResult.Invalid(REQUIRED, ProductField.Purchase)
    input.sale.isEmpty() -> ProductFormResult.Invalid(REQUIRED, ProductField.Sale)
    input.stock.isEmpty() -> ProductFormResult.Invalid(REQUIRED, ProductField.Stock)
    else -> null
}

private fun normalizedCode(value: String?): String =
    (value ?: "").replace(JS_WHITESPACE_RUN, "").jsTrim()

/** Formulaire « Ajouter un produit ». */
fun validateNewProduct(input: ProductFormInput, existing: List<Product>): ProductFormResult {
    requiredCheck(input)?.let { return it }

    val name = input.name.jsTrim()
    if (name.isEmpty()) {
        return ProductFormResult.Invalid("Veuillez saisir le nom du produit.", ProductField.Name)
    }

    val purchase = formNumber(input.purchase)
    if (!(purchase.isFinite() && purchase >= 0.0)) {
        return ProductFormResult.Invalid("Prix d'achat invalide.", ProductField.Purchase)
    }

    // Un prix de vente à 0 rendrait la vente impossible à encaisser.
    val sale = formNumber(input.sale)
    if (!(sale.isFinite() && sale >= 0.0) || sale <= 0.0) {
        return ProductFormResult.Invalid("Prix de vente invalide.", ProductField.Sale)
    }

    val stock = formNumber(input.stock)
    if (!(isInteger(stock) && stock >= 0.0)) {
        return ProductFormResult.Invalid("Stock initial invalide.", ProductField.Stock)
    }

    val threshold = formNumber(input.threshold)
    if (!(isInteger(threshold) && threshold >= 0.0)) {
        return ProductFormResult.Invalid("Seuil d'alerte invalide.", ProductField.Threshold)
    }

    val code = input.barcode.jsTrim()
    if (code.isNotEmpty() && existing.any { it.barcode == code }) {
        return ProductFormResult.Invalid("Ce code-barres existe déjà.", ProductField.Barcode)
    }

    return ProductFormResult.Valid(
        Product(
            id = newId(),
            name = name,
            purchasePrice = purchase,
            salePrice = sale,
            stock = stock.toInt(),
            stockThreshold = threshold.toInt(),
            barcode = code.ifEmpty { null },
            photo = input.photo,
            category = resolveCategory(input.category, existing)
        )
    )
}

/** Formulaire « Modifier un produit » : mêmes règles, avec les limites de stockage. */
fun validateEditedProduct(
    input: ProductFormInput,
    product: Product,
    existing: List<Product>
): ProductFormResult {
    requiredCheck(input)?.let { return it }

    val name = input.name.jsTrim()
    if (name.isEmpty()) {
        return ProductFormResult.Invalid("Veuillez saisir le nom du produit.", ProductField.Name)
    }
    if (name.length > Limits.NAME_MAX) {
        return ProductFormResult.Invalid(
            "Le nom est trop long (${Limits.NAME_MAX} caractères max).", ProductField.Name
        )
    }

    fun inRange(value: Double, max: Double) = value.isFinite() && value >= 0.0 && value <= max

    val purchase = formNumber(input.purchase)
    if (!inRange(purchase, Limits.MONEY_MAX)) {
        return ProductFormResult.Invalid("Prix d'achat invalide.", ProductField.Purchase)
    }

    val sale = formNumber(input.sale)
    if (!inRange(sale, Limits.MONEY_MAX) || sale <= 0.0) {
        return ProductFormResult.Invalid("Prix de vente invalide.", ProductField.Sale)
    }

    val stock = formNumber(input.stock)
    if (!(inRange(stock, Limits.STOCK_MAX) && isInteger(stock))) {
        return ProductFormResult.Invalid("Stock invalide.", ProductField.Stock)
    }

    val threshold = formNumber(input.threshold)
    if (!(inRange(threshold, Limits.STOCK_MAX) && isInteger(threshold))) {
        return ProductFormResult.Invalid("Seuil d'alerte invalide.", ProductField.Threshold)
    }

    val code = input.barcode.replace(JS_WHITESPACE_RUN, "")
    if (code.length > Limits.BARCODE_MAX) {
        return ProductFormResult.Invalid("Code-barres trop long.", ProductField.Barcode)
    }

    // Le code-barres ne doit pas appartenir à un AUTRE produit.
    val owner = if (code.isEmpty()) null else existing.firstOrNull { normalizedCode(it.barcode) == code }
    if (owner != null && owner.id != product.id) {
        return ProductFormResult.Invalid("Ce code-barres existe déjà.", ProductField.Barcode)
    }

    return ProductFormResult.Valid(
        product.copy(
            name = name,
            purchasePrice = purchase,
            salePrice = sale,
            stock = stock.toInt(),
            stockThreshold = threshold.toInt(),
            barcode = code.ifEmpty { null },
            photo = input.photo,
            category = resolveCategory(input.category, existing)
        )
    )
}
