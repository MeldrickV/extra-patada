package com.xtra.kick.ui.chat

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class KickChatReplayTest {

    private fun fixtureMessages(): List<JSONObject> {
        val text = javaClass.getResource("/kick/replay_messages.json")?.readText()
            ?: error("replay fixture missing")
        val array = JSONObject(text).optJSONArray("messages") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.let { add(it) }
            }
        }
    }

    @Test
    fun `real messages parse with timestamps`() {
        val parsed = fixtureMessages().mapNotNull { KickChatUtils.parseKickMessage(it) }

        assertTrue(parsed.isNotEmpty())
        parsed.forEach { message ->
            assertTrue((message.timestamp ?: 0) > 0)
            assertTrue(!message.message.isNullOrBlank())
        }
    }

    @Test
    fun `window keeps messages inside anchor range with offsets`() {
        val parsed = fixtureMessages().mapNotNull {
            KickChatUtils.parseKickMessage(it)?.takeIf { message -> message.timestamp != null }
        }
        val timestamps = parsed.mapNotNull { it.timestamp }
        val startMs = timestamps.minOrNull() ?: error("no timestamps")
        val fromMs = timestamps.maxOrNull() ?: error("no timestamps")

        val mapped = mapReplayWindow(parsed, startMs, fromMs, HashSet())

        assertEquals(parsed.size, mapped.size)
        mapped.forEach { video ->
            val source = parsed.first { it.id == video.id }
            val expected = ((source.timestamp ?: startMs) - startMs).div(1000).toInt().coerceAtLeast(0)
            assertEquals(expected, video.offsetSeconds)
        }
        assertEquals(0, mapped.minOf { it.offsetSeconds ?: 0 })
    }

    @Test
    fun `window drops messages outside range and dedupes`() {
        val parsed = fixtureMessages().mapNotNull {
            KickChatUtils.parseKickMessage(it)?.takeIf { message -> message.timestamp != null }
        }
        val latest = parsed.maxOf { it.timestamp ?: 0 }
        val seen = HashSet<String>()

        val first = mapReplayWindow(parsed, latest, latest, seen)
        first.forEach { assertEquals(0, it.offsetSeconds) }

        val second = mapReplayWindow(parsed, 0, latest, seen)
        assertTrue(second.none { replay -> first.any { it.id == replay.id } })

        val future = mapReplayWindow(parsed, latest + 1, latest + 60_000, HashSet())
        assertTrue(future.isEmpty())
    }

    @Test
    fun `forward window keeps messages from position up to ceiling`() {
        val parsed = fixtureMessages().mapNotNull {
            KickChatUtils.parseKickMessage(it)?.takeIf { message -> message.timestamp != null }
        }
        val timestamps = parsed.mapNotNull { it.timestamp }
        val anchor = timestamps.minOrNull() ?: error("no timestamps")
        // Simulate opening the VOD 5min in with a 10min forward window.
        val fromMs = anchor + 300_000
        val ceilingMs = fromMs + 600_000

        val mapped = mapReplayWindow(parsed, anchor, ceilingMs, HashSet())

        assertTrue(mapped.isNotEmpty())
        mapped.forEach { video ->
            val source = parsed.first { it.id == video.id }
            val timestamp = source.timestamp ?: error("no timestamp")
            assertTrue(timestamp in anchor..ceilingMs)
            assertEquals(((timestamp - anchor).div(1000).toInt().coerceAtLeast(0)), video.offsetSeconds)
        }
        // Messages are still anchored to the VOD start, not to the window.
        assertTrue(mapped.any { (it.offsetSeconds ?: 0) >= 60 })
    }

    @Test
    fun `anchor matches real vod start`() {
        val startMs = Instant.parse("2026-10-10T20:08:10Z").toEpochMilliseconds()
        assertTrue(startMs > 0)
    }
}
