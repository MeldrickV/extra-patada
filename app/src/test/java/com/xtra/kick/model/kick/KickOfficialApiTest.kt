package com.xtra.kick.model.kick

import com.xtra.kick.repository.parseSearchLivestream
import kotlinx.serialization.json.Json
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickOfficialApiTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun fixture(name: String): JSONObject {
        val text = javaClass.getResource("/kick/$name")?.readText() ?: error("fixture $name missing")
        return JSONObject(text)
    }

    @Test
    fun `v1 videos parse with ISO anchors`() {
        val array = fixture("channel_videos_v1.json").optJSONArray("data")!!
        val videos = buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.toString()?.let { add(json.decodeFromString<KickChannelVideoV1>(it)) }
            }
        }

        assertEquals(2, videos.size)
        val first = videos[0]
        assertEquals("2026-10-10T20:08:10Z", first.startTime)
        assertTrue(first.duration > 0)
        assertTrue(!first.title.isNullOrBlank())
        assertEquals("Just Chatting", first.category?.name)
        assertEquals("xqc", first.channel?.slug)
        assertTrue(!first.thumbnail.src.isNullOrBlank())
    }

    @Test
    fun `v1 matches v2 space format by start epoch`() {
        val array = fixture("channel_videos_v1.json").optJSONArray("data")!!
        val v1 = buildList {
            for (i in 0 until array.length()) {
                array.optJSONObject(i)?.toString()?.let { add(json.decodeFromString<KickChannelVideoV1>(it)) }
            }
        }

        val matched = v1.findByStart("2026-10-10 20:08:10")
        assertNotNull(matched)
        assertEquals("2026-10-10T20:08:10Z", matched?.startTime)

        assertNull(v1.findByStart(null))
        assertNull(v1.findByStart("not-a-date"))
        assertNull(v1.findByStart("2020-01-01 00:00:00"))
    }

    @Test
    fun `enriched search livestream parses and maps to followed stream`() {
        val array = fixture("search_livestream.json").optJSONArray("livestreams")!!
        val parsed = parseSearchLivestream(array.optJSONObject(0)!!)!!

        assertTrue(!parsed.slug.isNullOrBlank())
        assertTrue(!parsed.thumbnail.isNullOrBlank())
        assertTrue(!parsed.title.isNullOrBlank())
        assertTrue(parsed.viewerCount > 0)

        val stream = parsed.toFollowedStream()!!
        assertEquals(parsed.slug, stream.channelLogin)
        assertEquals(parsed.thumbnail, stream.thumbnailURL)
        assertEquals(parsed.title, stream.title)
        assertEquals(parsed.categoryName, stream.gameName)
        assertTrue((stream.channelId ?: "").startsWith("user_"))
    }

    @Test
    fun `followed stream needs slug`() {
        assertNull(KickSearchChannel(id = 1).toFollowedStream())
        assertNull(parseSearchLivestream(JSONObject()))
    }
}
