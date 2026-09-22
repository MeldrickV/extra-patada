package com.xtra.kick.model.kick

import com.xtra.kick.model.ui.Clip
import com.xtra.kick.model.ui.Game
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.model.ui.Tag
import com.xtra.kick.model.ui.User
import com.xtra.kick.model.ui.Video

fun KickLivestream.toStream(): Stream {
    return Stream(
        id = id,
        channelId = streamer?.user?.id,
        channelLogin = streamer?.channel?.slug,
        channelName = streamer?.user?.username,
        channelImageURL = streamer?.user?.profilePicture,
        gameId = metadata?.category?.id,
        gameSlug = metadata?.category?.slug,
        gameName = metadata?.category?.name,
        title = metadata?.title,
        thumbnailURL = thumbnailUrl,
        createdAt = startedAt,
        viewerCount = viewersCount,
        tags = metadata?.category?.tags?.takeIf { it.isNotEmpty() },
    )
}

fun KickCategory.toGame(): Game {
    return Game(
        id = id,
        slug = slug,
        name = name,
        boxArtURL = imageUrl,
        viewerCount = viewersCount,
        tags = tags.takeIf { it.isNotEmpty() }?.map { Tag(name = it) },
    )
}

fun KickFollowedChannel.toUser(): User {
    return User(
        id = "user_$id",
        login = slug,
        name = username,
        profileImageURL = profilePicture,
        accountFollow = true,
    )
}

fun KickChannelVideo.toVideo(
    channelId: String? = null,
    channelLogin: String? = null,
    channelName: String? = null,
    channelImageURL: String? = null,
): Video {
    val category = categories.firstOrNull()
    return Video(
        id = video.uuid?.takeIf { it.isNotBlank() } ?: slug,
        channelId = channelId ?: "user_${this.channelId.takeIf { it > 0 } ?: 0}",
        channelLogin = channelLogin,
        channelName = channelName,
        channelImageURL = channelImageURL,
        gameId = category?.id?.toString(),
        gameSlug = category?.slug,
        gameName = category?.name,
        title = sessionTitle,
        thumbnailURL = thumbnail.src,
        createdAt = startTime,
        viewCount = views.toInt(),
        durationSeconds = (duration / 1000L).toInt(),
        type = "archive",
        animatedPreviewURL = thumbnail.src,
    )
}

fun KickClip.toClip(
    channelId: String? = null,
    channelLogin: String? = null,
    channelName: String? = null,
    channelImageURL: String? = null,
): Clip {
    return Clip(
        id = id,
        channelId = channelId ?: "user_${this.channelId.takeIf { it > 0 } ?: 0}",
        channelLogin = channelLogin,
        channelName = channelName ?: channel?.username,
        channelImageURL = channelImageURL ?: channel?.profilePicture,
        gameId = category?.id?.toString(),
        gameSlug = category?.slug,
        gameName = category?.name,
        title = title,
        thumbnailURL = thumbnailUrl,
        createdAt = createdAt,
        viewCount = viewCount,
        durationSeconds = duration,
    )
}