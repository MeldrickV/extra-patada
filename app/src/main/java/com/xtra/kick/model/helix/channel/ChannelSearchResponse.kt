package com.xtra.kick.model.helix.channel

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class ChannelSearchResponse(
    val data: List<ChannelSearch>,
    val pagination: Pagination? = null,
)