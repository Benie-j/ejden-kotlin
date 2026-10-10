package com.ejden.app.data

private val LABEL_CONTROL = Regex("[\\u0000-\\u001F\\u007F]")

/** Texte sûr : sans caractères de contrôle, espaces réduits, coupé à la limite. */
fun cleanLabel(value: Any?, max: Int): String =
    if (value is String) {
        value.replace(LABEL_CONTROL, " ").replace(JS_WHITESPACE_RUN, " ").jsTrim().take(max)
    } else {
        ""
    }
