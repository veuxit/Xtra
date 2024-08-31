package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class SearchChannelsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val searchFor: Search,
    )

    @Serializable
    data class Search(
        val channels: Users,
    )

    @Serializable
    data class Users(
        val edges: List<Item>,
        val cursor: String? = null,
    )

    @Serializable
    data class Item(
        val item: User,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
        val followers: Followers? = null,
        val stream: Stream? = null,
    )

    @Serializable
    data class Followers(
        val totalCount: Int? = null,
    )

    @Serializable
    data class Stream(
        val type: String? = null,
    )
}