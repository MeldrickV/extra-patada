package com.xtra.kick.util

object KickPlayback {

    private const val THUMBNAILS_PATH = "/media/thumbnails/"
    private const val HLS_PATH = "/media/hls/"

    /**
     * Derives the VOD master playlist URL from a Kick video thumbnail.
     *
     * Kick thumbnails use one of two layouts:
     *  - `.../media/thumbnails/{uuid}/thumbnail-320x180.png` -> `.../media/hls/{uuid}/master.m3u8`
     *  - `.../media/thumbnails/{aws}/{session}/{yyyy}/{MM}/{dd}/{HH}/{mm}/{seg}/thumbnail-...png`
     *    -> `.../ivs/v1/{aws}/{session}/{yyyy}/{MM}/{dd}/{HH}/{mm}/{seg}/media/hls/master.m3u8`
     */
    fun vodMasterUrl(thumbnail: String?): String? {
        if (thumbnail.isNullOrBlank()) return null
        val markerIndex = thumbnail.indexOf(THUMBNAILS_PATH)
        if (markerIndex < 0) return null
        val base = thumbnail.substring(0, markerIndex)
        val rest = thumbnail.substring(markerIndex + THUMBNAILS_PATH.length)
            .substringBefore('.')
            .substringBefore('?')
        val segments = rest.split('/').filter { it.isNotBlank() }
        if (segments.size >= 8) {
            val year = segments[2].padStart(4, '0')
            val month = segments[3].padStart(2, '0')
            val day = segments[4].padStart(2, '0')
            val hour = segments[5].padStart(2, '0')
            val minute = segments[6].padStart(2, '0')
            return "$base/ivs/v1/${segments[0]}/${segments[1]}/" +
                "$year/$month/$day/$hour/$minute/${segments[7]}/media/hls/master.m3u8"
        }
        val uuid = segments.firstOrNull()
            ?.substringBefore("-thumb")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return "$base$HLS_PATH$uuid/master.m3u8"
    }
}