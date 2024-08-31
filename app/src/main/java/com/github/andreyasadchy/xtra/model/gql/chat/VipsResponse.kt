package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class VipsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: User,
    )

    @Serializable
    data class User(
        val vips: Vips,
    )

    @Serializable
    data class Vips(
        val edges: List<Item>,
    )

    @Serializable
    data class Item(
        val node: Vip,
    )

    @Serializable
    data class Vip(
        val id: String? = null,
        val login: String? = null,
    )
}