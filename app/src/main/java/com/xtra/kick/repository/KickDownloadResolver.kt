package com.xtra.kick.repository

import com.xtra.kick.model.kick.byLivestreamId

class KickDownloadResolver(
    private val kickRepository: KickRepository,
) {

    suspend fun resolveStreamUrl(channelLogin: String?): String? {
        if (channelLogin.isNullOrBlank()) return null
        return runCatching { kickRepository.getChannel(channelLogin)?.playbackUrl }.getOrNull()
    }

    data class ResolvedVideo(
        val source: String?,
        val createdAt: String?,
        val startTime: String?,
    )

    suspend fun resolveVideo(videoUuid: String?, channelLogin: String?): ResolvedVideo? {
        if (videoUuid.isNullOrBlank()) return null
        runCatching { kickRepository.getVideo(videoUuid) }.getOrNull()?.let { video ->
            if (!video.source.isNullOrBlank()) {
                return ResolvedVideo(
                    source = video.source,
                    createdAt = video.createdAt,
                    startTime = video.livestream?.startTime,
                )
            }
        }
        val login = channelLogin?.takeIf { it.isNotBlank() } ?: return null
        val match = runCatching { kickRepository.getChannelVideos(login) }.getOrNull()
            ?.firstOrNull { it.video?.uuid == videoUuid }
        val source = match?.source?.takeIf { it.isNotBlank() } ?: return null
        return ResolvedVideo(
            source = source,
            createdAt = match.video?.createdAt,
            startTime = match.startTime,
        )
    }

    data class ResolvedClip(
        val url: String?,
        val videoUuid: String?,
        val vodOffsetSeconds: Int?,
        val videoStartTime: String?,
    )

    suspend fun resolveClip(clipId: String?): ResolvedClip? {
        if (clipId.isNullOrBlank()) return null
        val clip = runCatching { kickRepository.getClip(clipId) }.getOrNull() ?: return null
        val url = clip.clipUrl?.takeIf { it.isNotBlank() }
            ?: clip.videoUrl?.takeIf { it.isNotBlank() }
        val video = clip.livestreamId?.let { livestreamId ->
            runCatching { clip.channel?.slug?.let { kickRepository.getChannelVideos(it) } }.getOrNull()
                .orEmpty()
                .byLivestreamId()[livestreamId]
        }
        return ResolvedClip(
            url = url,
            videoUuid = video?.video?.uuid?.takeIf { it.isNotBlank() },
            vodOffsetSeconds = clip.vodStartsAt.takeIf { it > 0 },
            videoStartTime = video?.startTime,
        )
    }
}
