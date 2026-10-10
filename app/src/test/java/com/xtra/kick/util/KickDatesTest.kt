package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Instant

class KickDatesTest {

    @Test
    fun `parses ISO instant with Z`() {
        val expected = Instant.parse("2026-10-10T21:06:37Z").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("2026-10-10T21:06:37Z"))
    }

    @Test
    fun `parses ISO instant with fractional seconds`() {
        val expected = Instant.parse("2026-10-10T20:17:50.612824Z").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("2026-10-10T20:17:50.612824Z"))
    }

    @Test
    fun `parses ISO instant with offset`() {
        val expected = Instant.parse("2026-10-10T20:08:10+00:00").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("2026-10-10T20:08:10+00:00"))
    }

    @Test
    fun `parses kick space format as UTC`() {
        val expected = Instant.parse("2026-10-10T20:08:10Z").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("2026-10-10 20:08:10"))
    }

    @Test
    fun `parses kick space format with fractional seconds`() {
        val expected = Instant.parse("2026-10-10T20:08:10.500Z").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("2026-10-10 20:08:10.500"))
    }

    @Test
    fun `trims whitespace`() {
        val expected = Instant.parse("2026-10-10T20:08:10Z").toEpochMilliseconds()
        assertEquals(expected, parseKickTimestampMs("  2026-10-10 20:08:10  "))
    }

    @Test
    fun `returns null for null blank or invalid`() {
        assertNull(parseKickTimestampMs(null))
        assertNull(parseKickTimestampMs(""))
        assertNull(parseKickTimestampMs("   "))
        assertNull(parseKickTimestampMs("not-a-date"))
        assertNull(parseKickTimestampMs("2026-13-40 25:70:70"))
        assertNull(parseKickTimestampMs("1728512890"))
    }
}
