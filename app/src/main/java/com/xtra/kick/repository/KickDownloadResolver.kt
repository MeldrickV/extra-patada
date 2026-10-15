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
        val login = channelLogin?.takeIf { it.isNotBlank() }
        val videos = login?.let { runCatching { kickRepository.getChannelVideos(it) }.getOrNull() }
        val match = videos?.firstOrNull { it.video?.uuid == videoUuid }
        match?.let {
            // The mint endpoint only knows new-format v1 ids (old uuids answer 404).
            val v1Id = runCatching {
                kickRepository.getV1IdForMatch(it.channelId, login, it.startTime)
            }.getOrNull()
            runCatching { kickRepository.mintPlaybackUrl(v1Id ?: videoUuid, login) }.getOrNull()
                ?.takeIf { url -> url.isNotBlank() }?.let { minted ->
                    return ResolvedVideo(
                        source = minted,
                        createdAt = null,
                        startTime = null,
                    )
                }
        }
        runCatching { kickRepository.mintPlaybackUrl(videoUuid, login) }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { minted ->
                return ResolvedVideo(
                    source = minted,
                    createdAt = null,
                    startTime = null,
                )
            }
        login ?: return null
        match?.source?.takeIf { it.isNotBlank() }?.let { source ->
            return ResolvedVideo(
                source = source,
                createdAt = match.video?.createdAt,
                startTime = match.startTime,
            )
        }
        // Last resort: probe the IVS paths from the listing thumbnail.
        val startMs = match?.startTime?.let { com.xtra.kick.util.parseKickTimestampMs(it) }
        val probed = runCatching {
            kickRepository.probeIvsMaster(match?.thumbnail?.src, startMs)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return ResolvedVideo(
            source = probed,
            createdAt = match?.video?.createdAt,
            startTime = match?.startTime,
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
