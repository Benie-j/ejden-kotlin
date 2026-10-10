package com.ejden.app.data

/** Nombre en texte simple, sans séparateur : 1500.0 -> "1500". */
fun plainNumber(value: Double): String =
    if (value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()
