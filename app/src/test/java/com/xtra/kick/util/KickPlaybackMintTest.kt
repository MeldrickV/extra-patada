package com.xtra.kick.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KickPlaybackMintTest {

    @Test
    fun `playback body carries the full player payload`() {
        val body = JSONObject(KickPlaybackMint.buildPlaybackBody("xqc", "vid-1", "es-ES"))

        val player = body.getJSONObject("video_player").getJSONObject("player")
        assertEquals("web", player.getString("player_name"))
        assertEquals("1.0.0", player.getString("player_version"))
        assertEquals("IVS Player", player.getString("player_software"))
        assertEquals("1.28.0", player.getString("player_software_version"))

        val videoPlayer = body.getJSONObject("video_player")
        assertTrue(videoPlayer.has("mux_sdk"))
        assertTrue(videoPlayer.has("pal_sdk"))
        assertTrue(videoPlayer.has("datazoom_sdk"))
        assertTrue(videoPlayer.has("google_ads_sdk"))

        val session = body.getJSONObject("video_session")
        assertEquals("vod", session.getString("page_type"))
        assertEquals("/xqc/videos/vid-1", session.getString("url_path"))

        val user = body.getJSONObject("user_session")
        assertEquals("unknown", user.getString("player_device_id"))
        assertEquals("es-ES", user.getString("browser_lang"))
    }

    @Test
    fun `playback body falls back to videos slug`() {
        val body = JSONObject(KickPlaybackMint.buildPlaybackBody("", "vid-1"))
        assertEquals("/videos/videos/vid-1", body.getJSONObject("video_session").getString("url_path"))
    }

    @Test
    fun `parses minted vod url`() {
        assertEquals(
            "https://stream.kick.com/master.m3u8?token=1",
            KickPlaybackMint.parseMintedVodUrl("""{"playback_url":{"vod":"https://stream.kick.com/master.m3u8?token=1"}}"""),
        )
    }

    @Test
    fun `parse returns null without vod url`() {
        assertNull(KickPlaybackMint.parseMintedVodUrl("""{"playback_url":{}}"""))
        assertNull(KickPlaybackMint.parseMintedVodUrl("""{}"""))
        assertNull(KickPlaybackMint.parseMintedVodUrl("not json"))
    }

    @Test
    fun `cache buster appends kick_ts`() {
        val plain = KickPlaybackMint.withCacheBuster("https://host/master.m3u8")
        assertTrue(plain.startsWith("https://host/master.m3u8?kick_ts="))

        val signed = KickPlaybackMint.withCacheBuster("https://host/master.m3u8?token=1")
        assertTrue(signed.startsWith("https://host/master.m3u8?token=1&kick_ts="))
    }
}
