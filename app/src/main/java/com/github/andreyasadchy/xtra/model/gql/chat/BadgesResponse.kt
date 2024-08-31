package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class BadgesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val badges: List<Badge>? = null,
        val user: UserBadges? = null,
    )

    @Serializable
    data class UserBadges(
        val broadcastBadges: List<Badge>,
    )

    @Serializable
    data class Badge(
        val setID: String? = null,
        val version: String? = null,
        val image1x: String? = null,
        val image2x: String? = null,
        val image4x: String? = null,
        val title: String? = null,
    )
}