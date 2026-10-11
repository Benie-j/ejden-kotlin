package com.ejden.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayItems<T>(val key: String, val label: String, val items: List<T>)

/** Regroupe des éléments déjà triés par jour (heure locale), avec « Aujourd'hui » et « Hier ». */
fun <T> groupByDay(
    items: List<T>,
    today: LocalDate,
    zone: ZoneId,
    createdAt: (T) -> String
): List<DayItems<T>> {
    val groups = ArrayList<DayItems<T>>()
    var currentKey = ""
    var currentLabel = ""
    var current = ArrayList<T>()

    fun flush() {
        if (current.isNotEmpty()) groups.add(DayItems(currentKey, currentLabel, current))
    }

    for (item in items) {
        val millis = parseDateMillis(createdAt(item))
        val date = millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val key = date?.toString() ?: "inconnu"
        if (key != currentKey) {
            flush()
            currentKey = key
            currentLabel = if (date == null) "Date inconnue" else dayLabel(date, today)
            current = ArrayList()
        }
        current.add(item)
    }
    flush()
    return groups
}
