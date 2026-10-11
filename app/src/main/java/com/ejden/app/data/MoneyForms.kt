package com.ejden.app.data

/** Champ fautif : le texte (description ou motif) ou le montant. */
enum class MoneyField { Text, Amount }

/** Équivalent de cleanAmount(Number(texte)) : vide = 0 = invalide. */
private fun amountOf(text: String): Double? {
    val t = text.jsTrim()
    val number = if (t.isEmpty()) 0.0 else (t.toDoubleOrNull() ?: Double.NaN)
    return cleanAmount(number)
}

private fun cleanText(raw: String): String = raw.replace(JS_WHITESPACE_RUN, " ").jsTrim()

sealed class ExpenseFormResult {
    data class Valid(val label: String, val amount: Double, val category: ExpenseCategory) : ExpenseFormResult()
    data class Invalid(val message: String, val field: MoneyField) : ExpenseFormResult()
}

fun validateExpenseForm(labelRaw: String, amountText: String, category: ExpenseCategory): ExpenseFormResult {
    val label = cleanText(labelRaw)
    val amount = amountOf(amountText)

    if (label.isEmpty()) return ExpenseFormResult.Invalid("Veuillez décrire la dépense.", MoneyField.Text)
    if (label.length > ModuleLimits.TEXT_MAX) {
        return ExpenseFormResult.Invalid(
            "Description trop longue (${ModuleLimits.TEXT_MAX} caractères max).", MoneyField.Text
        )
    }
    if (amount == null) return ExpenseFormResult.Invalid("Montant invalide.", MoneyField.Amount)
    return ExpenseFormResult.Valid(label, amount, category)
}

sealed class CashFormResult {
    data class Valid(
        val type: CashType,
        val amount: Double,
        val reason: String,
        /** Une sortie qui rend le solde négatif est acceptée, mais signalée. */
        val goesNegative: Boolean
    ) : CashFormResult()

    data class Invalid(val message: String, val field: MoneyField) : CashFormResult()
}

fun validateCashForm(type: CashType, amountText: String, reasonRaw: String, balance: Double): CashFormResult {
    val amount = amountOf(amountText)
    val reason = cleanText(reasonRaw)

    if (amount == null) return CashFormResult.Invalid("Montant invalide.", MoneyField.Amount)
    if (reason.isEmpty()) return CashFormResult.Invalid("Veuillez indiquer le motif.", MoneyField.Text)
    if (reason.length > ModuleLimits.TEXT_MAX) {
        return CashFormResult.Invalid("Motif trop long (${ModuleLimits.TEXT_MAX} caractères max).", MoneyField.Text)
    }
    return CashFormResult.Valid(type, amount, reason, type == CashType.Out && balance - amount < 0.0)
}

fun cashSavedMessage(type: CashType, goesNegative: Boolean): String = when {
    goesNegative -> "Enregistré. Attention : le solde de la caisse est négatif."
    type == CashType.In -> "Entrée enregistrée."
    else -> "Sortie enregistrée."
}
