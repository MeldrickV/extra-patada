package com.xtra.kick.model.ui

import android.os.Parcelable
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import kotlinx.parcelize.Parcelize

@Parcelize
class Stream(
    var id: String? = null,
    val channelId: String? = null,
    val channelLogin: String? = null,
    val channelName: String? = null,
    var channelImageURL: String? = null,
    var gameId: String? = null,
    var gameSlug: String? = null,
    var gameName: String? = null,
    var title: String? = null,
    val thumbnailURL: String? = null,
    var createdAt: String? = null,
    var viewerCount: Int? = null,
    val tags: List<String>? = null,
    val platform: String? = null,
) : Parcelable {

    val isKick: Boolean
        get() = platform == C.KICK || channelId?.startsWith(C.KICK_USER_PREFIX) == true
    val channelImage: String?
        get() = if (isKick) channelImageURL else TwitchApiHelper.getProfileImage(channelImageURL)
    val thumbnail: String?
        get() = if (isKick) thumbnailURL else TwitchApiHelper.getStreamThumbnail(thumbnailURL)
}
