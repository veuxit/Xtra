package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class VideoGamesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val video: Video,
    )

    @Serializable
    data class Video(
        val moments: Moments,
    )

    @Serializable
    data class Moments(
        val edges: List<Item>,
    )

    @Serializable
    data class Item(
        val node: Moment,
    )

    @Serializable
    data class Moment(
        val details: Details? = null,
        val positionMilliseconds: Int? = null,
        val durationMilliseconds: Int? = null,
    )

    @Serializable
    data class Details(
        val game: Game? = null,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val displayName: String? = null,
        val boxArtURL: String? = null,
    )
}