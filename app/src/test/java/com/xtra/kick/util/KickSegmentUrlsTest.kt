package com.xtra.kick.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KickSegmentUrlsTest {

    @Test
    fun `detects kick urls`() {
        assertTrue(KickSegmentUrls.isKickUrl("https://stream.kick.com/ivs/v1/196233775518/sess/master.m3u8"))
        assertTrue(KickSegmentUrls.isKickUrl("https://web.kick.com/api/v1/video/abc"))
        assertTrue(KickSegmentUrls.isKickUrl("https://kx-uploads.s3.us-east-2.amazonaws.com/abc/seg.m4s"))
        assertTrue(KickSegmentUrls.isKickUrl("https://cdn.example.com/ivs/v1/seg.m4s"))
        assertFalse(KickSegmentUrls.isKickUrl("https://usher.ttvnw.net/vod/1234.m3u8"))
        assertFalse(KickSegmentUrls.isKickUrl("not a url"))
    }

    @Test
    fun `absolute uris pass through`() {
        val absolute = "https://cdn.kick.com/seg.m4s?token=1"
        assertEquals(absolute, KickSegmentUrls.resolveUrl("https://stream.kick.com/a/b/master.m3u8", absolute))
    }

    @Test
    fun `relative uris resolve against playlist base`() {
        assertEquals(
            "https://stream.kick.com/a/b/seg.m4s",
            KickSegmentUrls.resolveUrl("https://stream.kick.com/a/b/master.m3u8", "seg.m4s"),
        )
        assertEquals(
            "https://usher.ttvnw.net/vod/index-1080p60.ts",
            KickSegmentUrls.resolveUrl("https://usher.ttvnw.net/vod/playlist.m3u8", "index-1080p60.ts"),
        )
    }

    @Test
    fun `range header covers exact byte span`() {
        assertEquals("bytes=100-199", KickSegmentUrls.rangeHeader(100, 100))
        assertEquals("bytes=0-1023", KickSegmentUrls.rangeHeader(0, 1024))
    }

    @Test
    fun `segment headers carry browser identity`() {
        val headers = KickSegmentUrls.segmentHeaders()
        assertTrue(headers["User-Agent"]?.contains("Mozilla") == true)
        assertTrue(headers["Referer"]?.contains("kick.com") == true)
    }
}
