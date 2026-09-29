package com.xtra.kick.model.helix.follow

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class FollowsResponse(
    val data: List<Follow>,
    val pagination: Pagination? = null,
)
