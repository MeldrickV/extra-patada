package com.xtra.kick.util.m3u8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class PlaylistUtilsTest {

    private fun parse(content: String): MediaPlaylist {
        return PlaylistUtils.parseMediaPlaylist(ByteArrayInputStream(content.toByteArray()))
    }

    @Test
    fun `parses segments with durations and titles`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.5,title one
            /seg1.ts
            #EXTINF:10.0,title two
            /seg2.ts
            #EXT-X-ENDLIST
            """.trimIndent()
        )

        assertEquals(10, playlist.targetDuration)
        assertEquals(2, playlist.segments.size)
        assertEquals("/seg1.ts", playlist.segments[0].uri)
        assertEquals(9.5f, playlist.segments[0].duration)
        assertEquals("title one", playlist.segments[0].title)
        assertEquals("/seg2.ts", playlist.segments[1].uri)
        assertEquals(10.0f, playlist.segments[1].duration)
        assertTrue(playlist.end)
        assertNull(playlist.initSegmentUri)
    }

    @Test
    fun `parses init segment map and program date time`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-VERSION:6
            #EXT-X-TARGETDURATION:6
            #EXT-X-PROGRAM-DATE-TIME:2026-09-22T10:00:00.000Z
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            /seg1.m4s
            #EXTINF:6.0,
            /seg2.m4s
            """.trimIndent()
        )

        assertEquals("init.mp4", playlist.initSegmentUri)
        assertEquals(2, playlist.segments.size)
        assertEquals("2026-09-22T10:00:00.000Z", playlist.segments[0].programDateTime)
        assertEquals("2026-09-22T10:00:00.000Z", playlist.segments[1].programDateTime)
        assertFalse(playlist.end)
    }

    @Test
    fun `parses date ranges with ad flags`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-DATERANGE:ID="ad-1",START-DATE="2026-09-22T10:00:00.000Z",PLANNED-DURATION=30.0,X-TV-TWITCH-AD-CUEPOINTS="1"
            #EXTINF:10.0,
            /seg1.ts
            #EXT-X-DATERANGE:ID="mid",START-DATE="2026-09-22T10:01:00.000Z",END-DATE="2026-09-22T10:01:30.000Z",DURATION=30.0
            #EXTINF:10.0,
            /seg2.ts
            """.trimIndent()
        )

        assertEquals(2, playlist.dateRanges.size)
        val ad = playlist.dateRanges[0]
        assertEquals("ad-1", ad.id)
        assertEquals(30.0f, ad.plannedDuration)
        assertTrue(ad.ad)

        val mid = playlist.dateRanges[1]
        assertEquals("mid", mid.id)
        assertEquals("2026-09-22T10:01:30.000Z", mid.endDate)
        assertEquals(30.0f, mid.duration)
        assertFalse(mid.ad)
    }

    @Test
    fun `ignores blank lines and list headers`() {
        val playlist = parse(
            """
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:5
            #EXT-X-MEDIA-SEQUENCE:0
            #EXTINF:5.0,
            /seg.ts

            #EXT-X-DISCONTINUITY
            #EXTINF:5.0,
            /seg2.ts
            """.trimIndent()
        )

        assertEquals(2, playlist.segments.size)
        assertEquals(5, playlist.targetDuration)
    }

    @Test
    fun `parses byte ranges with explicit and continued offsets`() {
        val playlist = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:6
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000@0
            /video.mp4
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:1000
            /video.mp4
            #EXTINF:6.0,
            #EXT-X-BYTERANGE:500@5000
            /other.mp4
            """.trimIndent()
        )

        assertEquals(3, playlist.segments.size)
        assertEquals(ByteRange(0, 1000), playlist.segments[0].byteRange)
        assertEquals(ByteRange(1000, 1000), playlist.segments[1].byteRange)
        assertEquals(ByteRange(5000, 500), playlist.segments[2].byteRange)
    }

    @Test
    fun `parses encryption method`() {
        val encrypted = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXT-X-KEY:METHOD=AES-128,URI="https://host/key"
            #EXTINF:10.0,
            /seg.ts
            """.trimIndent()
        )
        assertEquals("AES-128", encrypted.keyMethod)

        val plain = parse(
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            /seg.ts
            """.trimIndent()
        )
        assertNull(plain.keyMethod)
    }

    @Test
    fun `write then parse round-trips byte ranges`() {
        val original = MediaPlaylist(
            targetDuration = 6,
            dateRanges = emptyList(),
            initSegmentUri = "init.mp4",
            segments = listOf(
                Segment("/video.mp4", 6.0f, null, null, ByteRange(0, 1000)),
                Segment("/video.mp4", 6.0f, null, null, ByteRange(1000, 1000)),
            ),
            end = true,
        )

        val output = ByteArrayOutputStream()
        PlaylistUtils.writeMediaPlaylist(original, output)
        val replayed = parse(output.toString("UTF-8"))

        assertEquals(2, replayed.segments.size)
        assertEquals(ByteRange(0, 1000), replayed.segments[0].byteRange)
        assertEquals(ByteRange(1000, 1000), replayed.segments[1].byteRange)
    }

    @Test
    fun `write then parse round-trips content`() {
        val original = MediaPlaylist(
            targetDuration = 10,
            dateRanges = emptyList(),
            initSegmentUri = "init.mp4",
            segments = listOf(
                Segment("/seg1.m4s", 6.0f, null, null),
                Segment("/seg2.m4s", 6.0f, null, null),
            ),
            end = true,
        )

        val output = ByteArrayOutputStream()
        PlaylistUtils.writeMediaPlaylist(original, output)
        val replayed = parse(output.toString("UTF-8"))

        assertEquals(original.targetDuration, replayed.targetDuration)
        assertEquals(original.initSegmentUri, replayed.initSegmentUri)
        assertEquals(original.segments.size, replayed.segments.size)
        assertEquals(original.segments[0].uri, replayed.segments[0].uri)
        assertEquals(original.segments[0].duration, replayed.segments[0].duration)
        assertNotNull(replayed.initSegmentUri)
    }

    @Test
    fun `write emits endlist only when end is set`() {
        val output = ByteArrayOutputStream()
        PlaylistUtils.writeMediaPlaylist(
            MediaPlaylist(10, emptyList(), null, listOf(Segment("/seg.ts", 9.5f, null, null)), end = true),
            output,
        )
        val content = output.toString("UTF-8")
        assertTrue(content.contains("#EXT-X-ENDLIST"))
        assertTrue(content.contains("#EXT-X-PLAYLIST-TYPE:EVENT"))
    }

    @Test
    fun `write without endlist parses as growing playlist`() {
        val output = ByteArrayOutputStream()
        PlaylistUtils.writeMediaPlaylist(
            MediaPlaylist(
                10,
                emptyList(),
                "init.mp4",
                listOf(
                    Segment("/seg1.m4s", 6.0f, null, null),
                    Segment("/seg2.m4s", 6.0f, null, null),
                ),
                end = false,
            ),
            output,
            endList = false,
        )
        val content = output.toString("UTF-8")
        assertFalse(content.contains("#EXT-X-ENDLIST"))
        val replayed = parse(content)
        assertEquals(2, replayed.segments.size)
        assertFalse(replayed.end)
    }

    @Test
    fun `partial playlist uri points at sibling partial`() {
        assertEquals(
            "/sdcard/x/partial.m3u8",
            PlaylistUtils.partialPlaylistUriFor("/sdcard/x/12-34.m3u8"),
        )
        assertEquals(
            "content://tree%2Fdir%2Fpartial.m3u8",
            PlaylistUtils.partialPlaylistUriFor("content://tree%2Fdir%2F12-34.m3u8"),
        )
        assertNull(PlaylistUtils.partialPlaylistUriFor("/sdcard/x/clip.mp4"))
        assertNull(PlaylistUtils.partialPlaylistUriFor("https://host/master.m3u8?token=1"))
    }
}