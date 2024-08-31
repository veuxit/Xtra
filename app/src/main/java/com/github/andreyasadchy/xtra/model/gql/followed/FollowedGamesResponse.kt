package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class FollowedGamesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val currentUser: FollowedData,
    )

    @Serializable
    data class FollowedData(
        val followedGames: Games,
    )

    @Serializable
    data class Games(
        val nodes: List<Game>,
    )

    @Serializable
    data class Game(
        val id: String? = null,
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