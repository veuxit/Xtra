package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class FollowedChannelsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: FollowedData,
    )

    @Serializable
    data class FollowedData(
        val follows: Users,
    )

    @Serializable
    data class Users(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: User,
        val cursor: String? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
        val self: Self? = null,
    )

    @Serializable
    data class Self(
        val follower: Follower? = null,
    )

    @Serializable
    data class Follower(
        val followedAt: String? = null,
    )
}