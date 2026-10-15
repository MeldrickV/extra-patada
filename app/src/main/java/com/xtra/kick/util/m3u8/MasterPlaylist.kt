package com.xtra.kick.util.m3u8

import com.xtra.kick.model.VideoQuality

object MasterPlaylist {

    fun parseMasterVariants(playlist: String): List<VideoQuality> {
        val stableVariantIds = Regex("STABLE-VARIANT-ID=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
        val resolutions = Regex("RESOLUTION=(\\d+x\\d+)").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
        val frameRates = Regex("FRAME-RATE=([\\d.]+)\\b").findAll(playlist).mapNotNull { it.groups[1]?.value?.toFloatOrNull() }.toMutableList()
        val bitrates = Regex("BANDWIDTH=(\\d+)\\b").findAll(playlist).mapNotNull { it.groups[1]?.value?.toIntOrNull() }.toMutableList()
        val codecs = Regex("CODECS=\"(.+?)\"").findAll(playlist).mapNotNull { it.groups[1]?.value }.toMutableList()
        val urls = Regex("https://.*\\.m3u8").findAll(playlist).map(MatchResult::value).toMutableList()
        val withIds = stableVariantIds.mapIndexedNotNull { index, variantId ->
            urls.getOrNull(index)?.let { url ->
                VideoQuality(variantId, resolutions.getOrNull(index)?.substringAfter('x')?.toIntOrNull(), frameRates.getOrNull(index), bitrates.getOrNull(index), codecs.getOrNull(index), url)
            }
        }
        if (withIds.isNotEmpty()) return withIds
        return fallbackVariants(playlist)
    }

    private fun fallbackVariants(playlist: String): List<VideoQuality> {
        val result = mutableListOf<VideoQuality>()
        var pendingInf: String? = null
        playlist.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                pendingInf = line
            } else if (pendingInf != null) {
                val inf = pendingInf
                pendingInf = null
                if (line.isNotEmpty() && !line.startsWith("#") && line.contains(".m3u8") && inf != null) {
                    val url = line
                    val videoName = Regex("VIDEO=\"(.+?)\"").find(inf)?.groups?.get(1)?.value
                    val resolution = Regex("RESOLUTION=(\\d+x\\d+)").find(inf)?.groups?.get(1)?.value
                    val height = resolution?.substringAfter('x')?.toIntOrNull()
                    val frameRate = Regex("FRAME-RATE=([\\d.]+)").find(inf)?.groups?.get(1)?.value?.toFloatOrNull()
                    val bitrate = Regex("BANDWIDTH=(\\d+)").find(inf)?.groups?.get(1)?.value?.toIntOrNull()
                    val codecs = Regex("CODECS=\"(.+?)\"").find(inf)?.groups?.get(1)?.value
                    val base = height?.let { "${it}p" } ?: "source"
                    val derived = if (frameRate != null && frameRate > 30) "$base${frameRate.toInt()}" else base
                    result.add(VideoQuality(videoName ?: derived, height, frameRate, bitrate, codecs, url))
                }
            }
        }
        val best = result.maxByOrNull { it.bitrate ?: 0 }
        if (best != null && !best.name.equals(VideoQuality.SOURCE_QUALITY, true)) {
            result[result.indexOf(best)] = VideoQuality(VideoQuality.SOURCE_QUALITY, best.resolution, best.frameRate, best.bitrate, best.codecs, best.url)
        }
        return result
    }
}
