package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ModeratorsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: User,
    )

    @Serializable
    data class User(
        val mods: Mods,
    )

    @Serializable
    data class Mods(
        val edges: List<Item>,
    )

    @Serializable
    data class Item(
        val node: Node,
    )

    @Serializable
    data class Node(
        val id: String? = null,
        val login: String? = null,
    )
}