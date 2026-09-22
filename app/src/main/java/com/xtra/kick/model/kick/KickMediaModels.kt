package com.xtra.kick.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KickChannelsVideosResponse(
    val videos: List<KickChannelVideo> = emptyList(),
)

@Serializable
data class KickChannelVideo(
    val id: Long = 0,
    val slug: String? = null,
    @SerialName("channel_id") val channelId: Long = 0,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    @SerialName("start_time") val startTime: String? = null,
    val source: String? = null,
    val duration: Long = 0,
    val language: String? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    val views: Long = 0,
    val thumbnail: KickVideoThumbnail = KickVideoThumbnail(),
    val video: KickVideoReference = KickVideoReference(),
    val categories: List<KickVideoCategory> = emptyList(),
)

@Serializable
data class KickVideoThumbnail(
    val src: String? = null,
)

@Serializable
data class KickVideoReference(
    val id: Long = 0,
    val uuid: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val status: String? = null,
)

@Serializable
data class KickVideoCategory(
    val id: Long = 0,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class KickVideoResponse(
    val source: String? = null,
    val livestream: KickChannelLivestream? = null,
)

@Serializable
data class KickChannelsClipsResponse(
    val clips: List<KickClip> = emptyList(),
)

@Serializable
data class KickClip(
    val id: String = "",
    @SerialName("livestream_id") val livestreamId: String? = null,
    @SerialName("channel_id") val channelId: Long = 0,
    val title: String? = null,
    @SerialName("clip_url") val clipUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val privacy: String? = null,
    val likes: Long = 0,
    val views: Int = 0,
    @SerialName("view_count") val viewCount: Int = 0,
    val duration: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val category: KickClipCategory? = null,
    val creator: KickClipUser? = null,
    val channel: KickClipChannel? = null,
)

@Serializable
data class KickClipCategory(
    val id: Long = 0,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class KickClipUser(
    val id: Long = 0,
    val username: String? = null,
    val slug: String? = null,
)

@Serializable
data class KickClipChannel(
    val id: Long = 0,
    val username: String? = null,
    val slug: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

@Serializable
data class KickSingleClipResponse(
    val clip: KickClip = KickClip(),
)