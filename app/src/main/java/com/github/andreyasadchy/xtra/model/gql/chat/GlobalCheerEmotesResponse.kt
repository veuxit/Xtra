package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.game.GamesResponse.Data
import kotlinx.serialization.Serializable

@Serializable
data class GlobalCheerEmotesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val cheerConfig: CheerConfig,
    )

    @Serializable
    data class CheerConfig(
        val displayConfig: DisplayConfig,
        val groups: List<Item>,
    )

    @Serializable
    data class DisplayConfig(
        val backgrounds: List<String>? = null,
        val colors: List<Color>,
        val scales: List<String>? = null,
        val types: List<Type>? = null,
    )

    @Serializable
    data class Color(
        val bits: Int,
        val color: String? = null,
    )

    @Serializable
    data class Type(
        val animation: String? = null,
        val extension: String? = null,
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