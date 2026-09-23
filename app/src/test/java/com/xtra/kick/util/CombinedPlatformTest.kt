package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedPlatformTest {

    @Test
    fun `platform pref combined only for both value`() {
        assertTrue(platformPrefIsCombined(C.PLATFORM_BOTH))
        assertFalse(platformPrefIsCombined(C.PLATFORM_TWITCH))
        assertFalse(platformPrefIsCombined(null))
        assertFalse(platformPrefIsCombined(""))
    }

    @Test
    fun `append deduplicates across sources with same key`() {
        val seen = HashSet<String>()
        val target = mutableListOf<String>()
        appendDeduplicated(seen, target, listOf("a", "b", "a"), { it }, 10)
        assertEquals(listOf("a", "b"), target)
        appendDeduplicated(seen, target, listOf("b", "c"), { it }, 10)
        assertEquals(listOf("a", "b", "c"), target)
    }

    @Test
    fun `append keeps distinct keys for same id across platforms`() {
        val seen = HashSet<String>()
        val target = mutableListOf<String>()
        val key: (String) -> String? = { "$it" }
        appendDeduplicated(seen, target, listOf("kick|7", "|7"), key, 10)
        assertEquals(2, target.size)
    }

    @Test
    fun `append respects maxSize cap`() {
        val seen = HashSet<String>()
        val target = mutableListOf<String>()
        appendDeduplicated(seen, target, listOf("a", "b", "c", "d"), { it }, 2)
        assertEquals(listOf("a", "b"), target)
        assertEquals(setOf("a", "b"), seen)
    }

    @Test
    fun `append skips null keys without cap consumption`() {
        val seen = HashSet<String>()
        val target = mutableListOf<String>()
        appendDeduplicated(seen, target, listOf("a", null, "b", null), { it }, 5)
        assertEquals(listOf("a", "b"), target)
    }
}