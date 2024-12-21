package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
class FollowingUserResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    class Data(
        val user: User,
    )

    @Serializable
    class User(
        val self: Self? = null,
    )

    @Serializable
    class Self(
        val follower: Follower? = null,
    )

    @Serializable
    class Follower(
        val disableNotifications: Boolean? = null,
    )
}