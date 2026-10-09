package com.ejden.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val ISO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** Équivalent de new Date().toISOString(). */
fun toIso(instant: Instant): String = ISO_MILLIS.format(instant)

/** Équivalent de new Date(texte).getTime() ; null si illisible (NaN). */
fun parseDateMillis(value: String): Long? {
    val v = value.trim()
    try {
        return OffsetDateTime.parse(v).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        // on essaie le format suivant
    }
    try {
        return LocalDateTime.parse(v).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        // on essaie le format suivant
    }
    try {
        return LocalDate.parse(v).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    } catch (e: DateTimeParseException) {
        // illisible
    }
    return null
}

/** Date valide ou null (équivalent de validDate). */
fun validDate(value: Any?): String? =
    if (value is String && parseDateMillis(value) != null) value else null
