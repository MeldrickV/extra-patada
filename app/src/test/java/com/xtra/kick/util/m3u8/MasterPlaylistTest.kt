package com.xtra.kick.util.m3u8

import com.xtra.kick.model.VideoQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterPlaylistTest {

    private val twitchMaster = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=8454752,RESOLUTION=1920x1080,FRAME-RATE=60.000,CODECS="avc1.640028,mp4a.40.2",STABLE-VARIANT-ID="1080p60"
        https://usher.ttvnw.net/vod/1234-1080p60.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,FRAME-RATE=30.000,CODECS="avc1.4d401f,mp4a.40.2",STABLE-VARIANT-ID="720p30"
        https://usher.ttvnw.net/vod/1234-720p30.m3u8
    """.trimIndent()

    private val kickMaster = """
        #EXTM3U
        #EXT-X-STREAM-INF:BANDWIDTH=8454752,RESOLUTION=1920x1080,FRAME-RATE=60,CODECS="avc1.640028,mp4a.40.2"
        https://stream.kick.com/edge/1080p60/master.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,FRAME-RATE=30,CODECS="avc1.4d401f,mp4a.40.2"
        https://stream.kick.com/edge/720p30/master.m3u8
        #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,FRAME-RATE=30,CODECS="avc1.4d401f,mp4a.40.2"
        https://stream.kick.com/edge/360p30/master.m3u8
    """.trimIndent()

    @Test
    fun `parses twitch variants by stable id`() {
        val variants = MasterPlaylist.parseMasterVariants(twitchMaster)

        assertEquals(2, variants.size)
        assertEquals("1080p60", variants[0].name)
        assertEquals("https://usher.ttvnw.net/vod/1234-1080p60.m3u8", variants[0].url)
        assertEquals(1080, variants[0].resolution)
    }

    @Test
    fun `falls back to resolution names without stable ids`() {
        val variants = MasterPlaylist.parseMasterVariants(kickMaster)

        assertEquals(3, variants.size)
        val names = variants.map { it.name }
        assertTrue(names.contains(VideoQuality.SOURCE_QUALITY))
        assertTrue(names.contains("720p"))
        assertTrue(names.contains("360p"))
        val source = variants.first { it.name == VideoQuality.SOURCE_QUALITY }
        assertEquals("https://stream.kick.com/edge/1080p60/master.m3u8", source.url)
        assertEquals(1080, source.resolution)
    }

    @Test
    fun `prefers video attribute names and keeps relative urls`() {
        val master = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=8454752,RESOLUTION=1920x1080,FRAME-RATE=60,CODECS="avc1.640028,mp4a.40.2",VIDEO="1080p60"
            1080p60/master.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,FRAME-RATE=30,CODECS="avc1.4d401f,mp4a.40.2",VIDEO="720p"
            720p/master.m3u8
        """.trimIndent()
        val variants = MasterPlaylist.parseMasterVariants(master)

        assertEquals(2, variants.size)
        assertEquals(VideoQuality.SOURCE_QUALITY, variants[0].name)
        assertEquals("1080p60/master.m3u8", variants[0].url)
        assertEquals(1080, variants[0].resolution)
        assertEquals("720p", variants[1].name)
        assertEquals("720p/master.m3u8", variants[1].url)
    }

    @Test
    fun `returns empty when no variants`() {
        assertTrue(MasterPlaylist.parseMasterVariants("#EXTM3U\n#EXT-X-ENDLIST").isEmpty())
        assertTrue(MasterPlaylist.parseMasterVariants("").isEmpty())
    }
}
