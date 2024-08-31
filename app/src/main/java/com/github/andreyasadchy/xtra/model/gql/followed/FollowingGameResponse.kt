package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class FollowingGameResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val game: Game,
    )

    @Serializable
    data class Game(
        val self: Self? = null,
    )

    @Serializable
    data class Self(
        val follow: Follow? = null,
    )

    @Serializable
    data class Follow(
        val followedAt: String? = null,
    )
}