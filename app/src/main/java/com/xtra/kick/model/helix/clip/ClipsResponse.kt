package com.xtra.kick.model.helix.clip

import com.xtra.kick.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class ClipsResponse(
    val data: List<Clip>,
    val pagination: Pagination? = null,
)