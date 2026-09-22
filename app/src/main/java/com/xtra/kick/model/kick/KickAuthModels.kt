package com.xtra.kick.model.kick

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KickOAuthTokenResponse(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "Bearer",
    val scope: String? = null,
)

@Serializable
data class KickOAuthIntrospection(
    val active: Boolean = false,
    @SerialName("client_id") val clientId: String? = null,
    val sub: String? = null,
    val username: String? = null,
    val email: String? = null,
    val exp: Long? = null,
)

@Serializable
data class KickSelfUserResponse(
    val data: KickSelfUserData = KickSelfUserData(),
)

@Serializable
data class KickSelfUserData(
    val id: Long = 0,
    val username: String? = null,
    val slug: String? = null,
    val email: String? = null,
    @SerialName("profile_picture") val profilePicture: String? = null,
)

data class KickFollowedChannel(
    val id: Long = 0,
    val username: String? = null,
    val slug: String? = null,
    val profilePicture: String? = null,
)