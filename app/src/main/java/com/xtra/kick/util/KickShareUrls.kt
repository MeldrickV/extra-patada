package com.xtra.kick.util

import com.xtra.kick.repository.KickRepository

/**
 * Canonical kick.com share URLs, mirroring the in-player share.
 */
object KickShareUrls {

    fun channelShareUrl(channelLogin: String): String {
        return "https://kick.com/$channelLogin"
    }

    fun videoShareUrl(channelLogin: String, webId: String): String {
        return "https://kick.com/$channelLogin/videos/$webId"
    }

    fun clipShareUrl(channelLogin: String, clipId: String): String {
        return "https://kick.com/$channelLogin/clips/$clipId"
    }

    /**
     * Resolves the canonical web video id from the v1 endpoint by recording
     * start time. The web `/videos/` route only knows new-format ids.
     */
    suspend fun resolveVodWebId(kickRepository: KickRepository, channelId: String?, createdAt: String?): String? {
        val numericId = channelId?.removePrefix(C.KICK_USER_PREFIX)?.toLongOrNull() ?: return null
        val epoch = parseKickTimestampMs(createdAt) ?: return null
        return runCatching { kickRepository.getChannelVideosV1(numericId) }.getOrNull()
            ?.firstOrNull { parseKickTimestampMs(it.startTime) == epoch }
            ?.id?.takeIf { it.isNotBlank() }
    }
}
