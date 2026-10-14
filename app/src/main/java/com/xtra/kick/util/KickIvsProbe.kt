package com.xtra.kick.util

import java.util.Calendar
import java.util.TimeZone

/**
 * Direct IVS probing for Kick VODs that cannot be minted (new recordings
 * without an exposed `source`, subscriber-only without an entitled session).
 *
 * The media segments are not entitlement-gated even when the signed master
 * is, so a master found by probing plays and downloads. Session/segment ids
 * are parsed from the VOD thumbnail, whose path mirrors the IVS recording
 * path, and each (base x minute-offset) candidate is validated with HEAD.
 */
object KickIvsProbe {

    val BASE_URLS = listOf(
        "https://stream.kick.com/ivs/v1/196233775518",
        "https://stream.kick.com/3c81249a5ce0/ivs/v1/196233775518",
        "https://stream.kick.com/0f3cb0ebce7/ivs/v1/196233775518",
    )

    val MINUTE_OFFSETS = listOf(0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5)

    data class SessionSegment(val sessionId: String, val segmentId: String)

    /**
     * Parses session/segment from either thumbnail layout:
     * - legacy `.../video_thumbnails/{session}/{segment}/...`
     * - current `.../media/thumbnails/{aws}/{session}/{yyyy}/{MM}/{dd}/{HH}/{mm}/{seg}/...`
     */
    fun parseSessionSegment(thumbUrl: String?): SessionSegment? {
        if (thumbUrl.isNullOrBlank()) return null
        val clean = thumbUrl.substringBefore("?")
        val parts = clean.split("/").filter { it.isNotBlank() }
        val legacyIndex = parts.indexOf("video_thumbnails")
        if (legacyIndex >= 0 && legacyIndex + 2 < parts.size) {
            val sessionId = parts[legacyIndex + 1].takeIf { it.isNotBlank() } ?: return null
            val segmentId = parts[legacyIndex + 2].substringBefore(".").takeIf { it.isNotBlank() } ?: return null
            return SessionSegment(sessionId, segmentId)
        }
        val markerIndex = parts.indexOf("thumbnails")
        if (markerIndex >= 0 && parts.size - markerIndex - 1 >= 8) {
            val rest = parts.drop(markerIndex + 1)
            val sessionId = rest.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
            val segmentId = rest.getOrNull(7)?.substringBefore(".")?.takeIf { it.isNotBlank() } ?: return null
            return SessionSegment(sessionId, segmentId)
        }
        return null
    }

    /**
     * Builds master candidates, minute offsets outer and bases inner so every
     * base is tried at the exact start minute before drifting outward.
     */
    fun candidateUrls(
        sessionId: String,
        segmentId: String,
        startMs: Long,
        bases: List<String> = BASE_URLS,
        offsets: List<Int> = MINUTE_OFFSETS,
    ): List<String> {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return offsets.flatMap { offset ->
            calendar.timeInMillis = startMs + offset * 60_000L
            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH) + 1
            val day = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            bases.map { base ->
                "$base/$sessionId/$year/$month/$day/$hour/$minute/$segmentId/media/hls/master.m3u8"
            }
        }
    }
}
