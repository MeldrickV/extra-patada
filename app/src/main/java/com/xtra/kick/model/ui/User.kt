package com.xtra.kick.model.ui

import android.os.Parcelable
import com.xtra.kick.util.C
import com.xtra.kick.util.TwitchApiHelper
import kotlinx.parcelize.Parcelize

@Parcelize
class User(
    val id: String? = null,
    val login: String? = null,
    val name: String? = null,
    var profileImageURL: String? = null,
    val type: String? = null,
    val broadcasterType: String? = null,
    val createdAt: String? = null,
    val followerCount: Int? = null,
    val bannerImageURL: String? = null,
    var lastBroadcast: String? = null,
    val isLive: Boolean? = false,
    var followedAt: String? = null,
    var accountFollow: Boolean = false,
    val localFollow: Boolean = false,
    val platform: String? = null,
) : Parcelable {

    val isKick: Boolean
        get() = platform == C.KICK || id?.startsWith(C.KICK_USER_PREFIX) == true
    val profileImage: String?
        get() = if (isKick) profileImageURL else TwitchApiHelper.getProfileImage(profileImageURL)
}