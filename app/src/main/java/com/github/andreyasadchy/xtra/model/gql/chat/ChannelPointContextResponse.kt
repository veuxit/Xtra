package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ChannelPointContextResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val community: Community,
    )

    @Serializable
    data class Community(
        val channel: Channel,
    )

    @Serializable
    data class Channel(
        val self: Self,
    )

    @Serializable
    data class Self(
        val communityPoints: Points,
    )

    @Serializable
    data class Points(
        val balance: Int? = null,
        val availableClaim: Claim? = null,
    )

    @Serializable
    data class Claim(
        val id: String? = null,
    )
}