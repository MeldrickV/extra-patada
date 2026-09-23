package com.xtra.kick.util

object KickPlayback {

    private const val THUMBNAILS_PATH = "/media/thumbnails/"
    private const val HLS_PATH = "/media/hls/"

    fun vodMasterUrl(thumbnail: String?): String? {
        if (thumbnail.isNullOrBlank()) return null
        val markerIndex = thumbnail.indexOf(THUMBNAILS_PATH)
        if (markerIndex < 0) return null
        val base = thumbnail.substring(0, markerIndex)
        val fileName = thumbnail.substring(markerIndex + THUMBNAILS_PATH.length).substringBefore('/')
        val uuid = fileName
            .substringBefore('.')
            .substringBefore("-thumb")
            .takeIf { it.isNotBlank() }
            ?: return null
        return "$base$HLS_PATH$uuid/master.m3u8"
    }
}