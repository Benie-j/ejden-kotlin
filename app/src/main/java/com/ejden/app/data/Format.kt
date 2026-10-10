package com.ejden.app.data

/** Équivalent de Math.round(n).toLocaleString("fr-FR") : groupes de 3 séparés par une espace fine insécable. */
fun formatNumber(value: Double): String {
    val rounded = Math.round(value)
    val negative = rounded < 0
    val digits = Math.abs(rounded).toString()
    val grouped = StringBuilder()
    for ((index, ch) in digits.withIndex()) {
        if (index > 0 && (digits.length - index) % 3 == 0) grouped.append('\u202F')
        grouped.append(ch)
    }
    return (if (negative) "-" else "") + grouped.toString()
}
