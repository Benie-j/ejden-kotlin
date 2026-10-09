package com.ejden.app.data

object ModuleLimits {
    const val TEXT_MAX = 80
    const val PHONE_MAX = 20
    const val AMOUNT_MAX = 100_000_000.0
}

/** Montant en FCFA : entier, > 0, borné. Sinon null (arrondi comme JavaScript). */
fun cleanAmount(value: Any?): Double? {
    if (value !is Number) return null
    val d = value.toDouble()
    if (!d.isFinite()) return null
    val rounded = Math.round(d)
    return if (rounded > 0 && rounded <= ModuleLimits.AMOUNT_MAX) rounded.toDouble() else null
}
