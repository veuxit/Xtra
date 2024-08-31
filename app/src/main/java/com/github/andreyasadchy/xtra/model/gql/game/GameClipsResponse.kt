package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import com.github.andreyasadchy.xtra.model.gql.stream.StreamsResponse.Game
import kotlinx.serialization.Serializable

@Serializable
data class GameClipsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val game: Game,
    )

    @Serializable
    data class Game(
        val clips: Clips,
    )

    @Serializable
    data class Clips(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: Clip,
        val cursor: String? = null,
    )

    @Serializable
    data class Clip(
        val slug: String? = null,
        val broadcaster: User? = null,
        val title: String? = null,
        val viewCount: Int? = null,
        val createdAt: String? = null,
        val thumbnailURL: String? = null,
        val durationSeconds: Double? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )
}