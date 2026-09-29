package com.xtra.kick.model.helix.follower

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class FollowersResponse(
    val data: List<Follower>,
    val pagination: Pagination? = null,
)
