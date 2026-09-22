package com.xtra.kick.model.gql.tag

import com.xtra.kick.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class TagResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val contentTag: ContentTag
    )

    @Serializable
    class ContentTag(
        val localizedName: String? = null,
    )
}
