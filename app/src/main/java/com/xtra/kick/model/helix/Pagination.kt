package com.xtra.kick.model.helix

import kotlinx.serialization.Serializable

@Serializable
class Pagination(
    val cursor: String? = null,
)