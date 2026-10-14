package com.xtra.kick.util

import org.json.JSONObject

/**
 * Mirrors the web player's signed-playback request (`POST .../stream/{id}/playback`).
 *
 * Kick rejects the call with 400 unless the whole player payload is present, so
 * none of it is optional. Returns null when no URL is minted, e.g. a
 * subscriber-only VOD without an entitled session.
 */
object KickPlaybackMint {

    fun buildPlaybackBody(channelSlug: String, videoId: String, browserLang: String = "en-US"): String {
        val slug = channelSlug.takeIf { it.isNotBlank() } ?: "videos"
        return JSONObject()
            .put(
                "video_player",
                JSONObject()
                    .put(
                        "player",
                        JSONObject()
                            .put("player_name", "web")
                            .put("player_version", "1.0.0")
                            .put("player_software", "IVS Player")
                            .put("player_software_version", "1.28.0"),
                    )
                    .put("mux_sdk", JSONObject().put("sdk_available", false))
                    .put("pal_sdk", JSONObject().put("sdk_available", false).put("nonce", ""))
                    .put(
                        "datazoom_sdk",
                        JSONObject()
                            .put("sdk_available", false)
                            .put("datazoom_sdk_version", "")
                            .put("om_sdk_version", ""),
                    )
                    .put("google_ads_sdk", JSONObject().put("sdk_available", false)),
            )
            .put(
                "video_session",
                JSONObject()
                    .put("page_type", "vod")
                    .put("player_remote_played", false)
                    .put("enable_sampling", false)
                    .put("url_path", "/$slug/videos/$videoId")
                    .put("autoplay_behaviour", "auto")
                    .put("play_muted", false)
                    .put("viewer_connection_type", ""),
            )
            .put(
                "user_session",
                JSONObject()
                    .put("session_id", "")
                    .put("player_device_id", "unknown")
                    .put("browser_lang", browserLang)
                    .put("non_personalised_ads", false)
                    .put("ad_targeting", ""),
            )
            .toString()
    }

    fun parseMintedVodUrl(body: String): String? {
        return runCatching {
            JSONObject(body).optJSONObject("playback_url")
                ?.optString("vod")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** Cache-buster so freshly processed VODs never play a stale master. */
    fun withCacheBuster(url: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}kick_ts=${System.currentTimeMillis()}"
    }
}
