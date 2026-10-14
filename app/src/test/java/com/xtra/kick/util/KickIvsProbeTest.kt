package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickIvsProbeTest {

    @Test
    fun `parses legacy video_thumbnails layout`() {
        val parsed = KickIvsProbe.parseSessionSegment(
            "https://images.kick.com/video_thumbnails/sess-1/seg-9/thumbnail-320x180.png"
        )
        assertEquals(KickIvsProbe.SessionSegment("sess-1", "seg-9"), parsed)
    }

    @Test
    fun `parses current media thumbnails layout`() {
        val parsed = KickIvsProbe.parseSessionSegment(
            "https://stream.kick.com/media/thumbnails/aws1/sess1/2024/3/4/4/19/seg1/thumbnail-320x180.png?x=1"
        )
        assertEquals(KickIvsProbe.SessionSegment("sess1", "seg1"), parsed)
    }

    @Test
    fun `parse returns null without session info`() {
        assertNull(KickIvsProbe.parseSessionSegment(null))
        assertNull(KickIvsProbe.parseSessionSegment(""))
        assertNull(KickIvsProbe.parseSessionSegment("https://images.kick.com/stream-thumb.png"))
        assertNull(KickIvsProbe.parseSessionSegment("https://host/video_thumbnails/only-session/"))
    }

    @Test
    fun `candidates try every base at start minute first`() {
        val urls = KickIvsProbe.candidateUrls("sess1", "seg1", 1_709_524_800_000L)
        assertEquals(KickIvsProbe.BASE_URLS.size * KickIvsProbe.MINUTE_OFFSETS.size, urls.size)
        // Offset 0 (2024-03-04 04:00 UTC) across all bases comes first.
        assertTrue(urls[0].endsWith("/sess1/2024/3/4/4/0/seg1/media/hls/master.m3u8"))
        assertTrue(urls[1].endsWith("/sess1/2024/3/4/4/0/seg1/media/hls/master.m3u8"))
        assertTrue(urls[2].endsWith("/sess1/2024/3/4/4/0/seg1/media/hls/master.m3u8"))
        assertEquals(KickIvsProbe.BASE_URLS[0], urls[0].substringBefore("/sess1/"))
        // Then minute +1.
        assertTrue(urls[3].endsWith("/sess1/2024/3/4/4/1/seg1/media/hls/master.m3u8"))
    }
}
