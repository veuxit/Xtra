package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import kotlinx.serialization.Serializable

@Serializable
data class GamesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val directoriesWithTags: Games,
    )

    @Serializable
    data class Games(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: Game,
        val cursor: String? = null,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val slug: String? = null,
        val displayName: String? = null,
        val avatarURL: String? = null,
        val viewersCount: Int? = null,
        val tags: List<Tag>? = null,
    )

    @Serializable
    data class Tag(
        val id: String? = null,
        val localizedName: String? = null,
    )
}