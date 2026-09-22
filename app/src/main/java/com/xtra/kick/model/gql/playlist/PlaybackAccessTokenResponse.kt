package com.xtra.kick.model.gql.playlist

import com.xtra.kick.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class PlaybackAccessTokenResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val streamPlaybackAccessToken: PlaybackAccessToken? = null,
        val videoPlaybackAccessToken: PlaybackAccessToken? = null,
    )

    @Serializable
    class PlaybackAccessToken(
        val value: String? = null,
        val signature: String? = null,
    )
}