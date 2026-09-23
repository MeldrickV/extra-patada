package com.xtra.kick.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KickStatus(
    val error: Boolean = false,
    val code: Int = 0,
    val message: String = "",
)

@Serializable
data class KickLivestreamsResponse(
    val status: KickStatus = KickStatus(),
    val data: KickLivestreamsData = KickLivestreamsData(),
)

@Serializable
data class KickLivestreamsData(
    val livestreams: List<KickLivestream> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class KickLivestream(
    val id: String? = null,
    val streamer: KickStreamer? = null,
    val metadata: KickLivestreamMetadata? = null,
    @SerialName("viewers_count") val viewersCount: Int = 0,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
)

@Serializable
data class KickStreamer(
    val user: KickStreamerUser? = null,
    val channel: KickStreamerChannel? = null,
)

@Serializable
data class KickStreamerUser(
    val id: String? = null,
    val username: String? = null,
    @SerialName("is_verified") val isVerified: Boolean = false,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

@Serializable
data class KickStreamerChannel(
    val id: String? = null,
    val slug: String? = null,
    @SerialName("banner_picture") val bannerPicture: String? = null,
    val description: String? = null,
)

@Serializable
data class KickLivestreamMetadata(
    val title: String? = null,
    val language: String? = null,
    val category: KickCategory? = null,
)

@Serializable
data class KickCategory(
    val id: String? = null,
    val name: String? = null,
    val slug: String? = null,
    val tags: List<String> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("viewers_count") val viewersCount: Int = 0,
)

@Serializable
data class KickCategoriesResponse(
    val status: KickStatus = KickStatus(),
    val data: KickCategoriesData = KickCategoriesData(),
)

@Serializable
data class KickCategoriesData(
    val categories: List<KickCategory> = emptyList(),
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class KickChannelResponse(
    val id: Long = 0,
    val slug: String? = null,
    @SerialName("playback_url") val playbackUrl: String? = null,
    @SerialName("followers_count") val followersCount: Long = 0,
    val user: KickChannelUser? = null,
    val chatroom: KickChatroom? = null,
    val livestream: KickChannelLivestream? = null,
)

@Serializable
data class KickChannelUser(
    val id: Long = 0,
    val username: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

@Serializable
data class KickChatroom(
    val id: Long = 0,
    val name: String? = null,
)

@Serializable
data class KickChannelLivestream(
    val id: Long = 0,
    @SerialName("session_title") val sessionTitle: String? = null,
    @SerialName("is_live") val isLive: Boolean = false,
    val category: KickCategory? = null,
    @SerialName("viewer_count") val viewerCount: Int = 0,
    @SerialName("started_at") val startedAt: String? = null,
)

data class KickRealtimeConnectionInfo(
    val url: String,
    val token: String,
)

data class KickSearchChannel(
    val id: Long,
    val username: String? = null,
    val slug: String? = null,
    val profilePicture: String? = null,
    val isLive: Boolean = false,
    val title: String? = null,
    val thumbnail: String? = null,
    val startedAt: String? = null,
    val viewerCount: Int = 0,
    val categoryId: String? = null,
    val categorySlug: String? = null,
    val categoryName: String? = null,
)
