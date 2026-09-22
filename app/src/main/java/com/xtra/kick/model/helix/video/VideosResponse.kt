package com.xtra.kick.model.helix.video

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class VideosResponse(
    val data: List<Video>,
    val pagination: Pagination? = null,
)