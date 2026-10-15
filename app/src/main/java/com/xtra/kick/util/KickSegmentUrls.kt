package com.xtra.kick.util

import java.io.IOException

/**
 * Shared URL/header rules for media-segment downloads.
 *
 * Twitch serves relative `.ts` URIs under the playlist directory with
 * long-lived tokens; Kick serves absolute, short-lived signed URLs (fMP4).
 * Resolving every URI against its playlist base keeps both working, and the
 * browser headers keep Kick's edge from rejecting segment requests.
 */
object KickSegmentUrls {

    fun isKickUrl(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("")
        return host.endsWith("kick.com") || url.contains("kx-uploads") || url.contains("/ivs/")
    }

    /** Absolute URIs pass through untouched; relative ones resolve against the playlist URL. */
    fun resolveUrl(playlistUrl: String, uri: String): String {
        if (uri.startsWith("http://") || uri.startsWith("https://")) return uri
        return runCatching { java.net.URI(playlistUrl).resolve(uri).toString() }.getOrDefault(uri)
    }

    fun segmentHeaders(): Map<String, String> = mapOf(
        "User-Agent" to KickApiHelper.BROWSER_USER_AGENT,
        "Referer" to "${KickApiHelper.WEBSITE_BASE_URL}/",
        "Origin" to KickApiHelper.WEBSITE_BASE_URL,
    )

    fun rangeHeader(start: Long, length: Long): String {
        return "bytes=$start-${start + length - 1}"
    }
}

/** Thrown when a segment/playlist request answers with an HTTP error. */
class SegmentHttpException(val code: Int) : IOException("Segment request failed: $code")
