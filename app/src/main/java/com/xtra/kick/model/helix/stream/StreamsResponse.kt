package com.xtra.kick.model.helix.stream

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class StreamsResponse(
    val data: List<Stream>,
    val pagination: Pagination? = null,
)