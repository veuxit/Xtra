package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class SearchVideosResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val searchFor: Search,
    )

    @Serializable
    data class Search(
        val videos: Videos,
    )

    @Serializable
    data class Videos(
        val edges: List<Item>,
        val cursor: String? = null,
    )

    @Serializable
    data class Item(
        val item: Video,
    )

    @Serializable
    data class Video(
        val id: String? = null,
        val owner: User? = null,
        val game: Game? = null,
        val title: String? = null,
        val createdAt: String? = null,
        val previewThumbnailURL: String? = null,
        val viewCount: Int? = null,
        val lengthSeconds: Int? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val slug: String? = null,
        val displayName: String? = null,
    )
}