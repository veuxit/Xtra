package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import kotlinx.serialization.Serializable

@Serializable
data class UserEmotesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val channel: Channel,
    )

    @Serializable
    data class Channel(
        val self: Self,
    )

    @Serializable
    data class Self(
        val availableEmoteSetsPaginated: Sets,
    )

    @Serializable
    data class Sets(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: Set,
        val cursor: String? = null,
    )

    @Serializable
    data class Set(
        val emotes: List<Emote>,
        val owner: User? = null,
    )

    @Serializable
    data class Emote(
        val token: String? = null,
        val id: String? = null,
        val setID: String? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
    )
}