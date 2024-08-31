package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import kotlinx.serialization.Serializable

@Serializable
data class FollowedStreamsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val currentUser: FollowedData,
    )

    @Serializable
    data class FollowedData(
        val followedLiveUsers: Users,
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
        val stream: Stream? = null,
    )

    @Serializable
    data class Stream(
        val id: String? = null,
        val game: Game? = null,
        val type: String? = null,
        val title: String? = null,
        val viewersCount: Int? = null,
        val previewImageURL: String? = null,
        val freeformTags: List<Tag>? = null,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val displayName: String? = null,
    )

    @Serializable
    data class Tag(
        val name: String? = null,
    )
}