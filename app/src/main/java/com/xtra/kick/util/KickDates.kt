package com.xtra.kick.util

import kotlin.time.Instant

private val SPACE_DATETIME_REGEX = Regex("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$")

fun parseKickTimestampMs(raw: String?): Long? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    parseInstantMs(value)?.let { return it }
    if (SPACE_DATETIME_REGEX.matches(value)) {
        return parseInstantMs(value.replace(' ', 'T') + "Z")
    }
    return null
}

private fun parseInstantMs(value: String): Long? {
    return try {
        Instant.parse(value).toEpochMilliseconds().takeIf { it > 0 }
    } catch (e: IllegalArgumentException) {
        null
    }
}
