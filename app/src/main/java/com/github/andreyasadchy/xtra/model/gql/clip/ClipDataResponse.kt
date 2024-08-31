package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ClipDataResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val clip: Clip,
    )

    @Serializable
    data class Clip(
        val broadcaster: User? = null,
        val videoOffsetSeconds: Int? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )
}