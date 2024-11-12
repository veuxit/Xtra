package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class FollowingUserResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: User,
    )

    @Serializable
    data class User(
        val self: Self? = null,
    )

    @Serializable
    data class Self(
        val follower: Follower? = null,
    )

    @Serializable
    data class Follower(
        val disableNotifications: Boolean? = null,
    )
}