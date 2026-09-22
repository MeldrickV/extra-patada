package com.xtra.kick.model.helix.chat

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class UserEmotesResponse(
    val template: String,
    val data: List<EmoteTemplate>,
    val pagination: Pagination? = null,
)