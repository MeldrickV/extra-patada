package com.xtra.kick.model.kick

import com.xtra.kick.model.ui.Game
import com.xtra.kick.model.ui.Stream
import com.xtra.kick.model.ui.Tag

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