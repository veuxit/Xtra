package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class SearchGameTagsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val searchCategoryTags: List<Tag>,
    )

    @Serializable
    data class Tag(
        val id: String? = null,
        val localizedName: String? = null,
        val scope: String? = null,
    )
}