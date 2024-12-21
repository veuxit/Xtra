package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class FollowingGameResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val game: Game,
    )

    @Serializable
    class Game(
        val self: Self? = null,
    )

    @Serializable
    class Self(
        val follow: Follow? = null,
    )

    @Serializable
    class Follow(
        val followedAt: String? = null,
    )
}