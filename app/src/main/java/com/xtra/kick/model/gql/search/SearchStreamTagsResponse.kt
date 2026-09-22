package com.xtra.kick.model.gql.search

import com.xtra.kick.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class SearchStreamTagsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val searchFreeformTags: Tags,
    )

    @Serializable
    class Tags(
        val edges: List<Item>,
    )

    @Serializable
    class Item(
        val node: Tag,
    )

    @Serializable
    class Tag(
        val tagName: String? = null,
    )
}