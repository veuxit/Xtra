package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class ChannelCheerEmotesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val channel: Channel,
    )

    @Serializable
    data class Channel(
        val cheer: CheerConfig,
    )

    @Serializable
    data class CheerConfig(
        val cheerGroups: List<Item>,
    )

    @Serializable
    data class Item(
        val nodes: List<Group>,
        val templateURL: String,
    )

    @Serializable
    data class Group(
        val tiers: List<Tier>,
        val prefix: String,
    )

    @Serializable
    data class Tier(
        val bits: Int,
    )
}