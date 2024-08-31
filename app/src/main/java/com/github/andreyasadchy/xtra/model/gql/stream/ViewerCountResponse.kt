package com.github.andreyasadchy.xtra.model.gql.stream

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ViewerCountResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: User,
    )

    @Serializable
    data class User(
        val stream: Stream? = null,
    )

    @Serializable
    data class Stream(
        val id: String? = null,
        val viewersCount: Int? = null,
    )
}