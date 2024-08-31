package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ClipVideoResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val clip: Clip,
    )

    @Serializable
    data class Clip(
        val video: Video? = null,
        val durationSeconds: Double? = null,
        val videoOffsetSeconds: Int? = null,
    )

    @Serializable
    data class Video(
        val id: String? = null,
    )
}