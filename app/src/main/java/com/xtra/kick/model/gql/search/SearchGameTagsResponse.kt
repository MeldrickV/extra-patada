package com.xtra.kick.model.gql.search

import com.xtra.kick.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class SearchGameTagsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val searchCategoryTags: List<Tag>,
    )

    @Serializable
    class Tag(
        val id: String? = null,
        val localizedName: String? = null,
    )
}