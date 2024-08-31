package com.github.andreyasadchy.xtra.model.gql.clip

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ClipUrlsResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val clip: Clip,
    )

    @Serializable
    data class Clip(
        val playbackAccessToken: PlaybackAccessToken? = null,
        val videoQualities: List<Quality>,
    )

    @Serializable
    data class PlaybackAccessToken(
        val value: String? = null,
        val signature: String? = null,
    )

    @Serializable
    data class Quality(
        val sourceURL: String,
        val frameRate: Float? = null,
        val quality: String? = null,
    )
}