package com.github.andreyasadchy.xtra.model.gql.playlist

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class PlaybackAccessTokenResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val streamPlaybackAccessToken: PlaybackAccessToken? = null,
        val videoPlaybackAccessToken: PlaybackAccessToken? = null,
    )

    @Serializable
    data class PlaybackAccessToken(
        val value: String? = null,
        val signature: String? = null,
    )
}