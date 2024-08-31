package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class SearchGamesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val searchFor: Search,
    )

    @Serializable
    data class Search(
        val games: Games,
    )

    @Serializable
    data class Games(
        val edges: List<Item>,
        val cursor: String? = null,
    )

    @Serializable
    data class Item(
        val item: Game,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val slug: String? = null,
        val displayName: String? = null,
        val boxArtURL: String? = null,
        val viewersCount: Int? = null,
        val tags: List<Tag>? = null,
    )

    @Serializable
    data class Tag(
        val id: String? = null,
        val localizedName: String? = null,
    )
}