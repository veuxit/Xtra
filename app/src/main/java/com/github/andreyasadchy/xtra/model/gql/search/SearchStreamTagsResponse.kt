package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class SearchStreamTagsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val searchFreeformTags: Tags,
    )

    @Serializable
    data class Tags(
        val edges: List<Item>,
    )

    @Serializable
    data class Item(
        val node: Tag,
    )

    @Serializable
    data class Tag(
        val tagName: String? = null,
    )
}