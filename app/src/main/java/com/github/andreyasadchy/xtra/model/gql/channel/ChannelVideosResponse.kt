package com.github.andreyasadchy.xtra.model.gql.channel

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import kotlinx.serialization.Serializable

@Serializable
data class ChannelVideosResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: Channel,
    )

    @Serializable
    data class Channel(
        val videos: Videos? = null,
    )

    @Serializable
    data class Videos(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: Video,
        val cursor: String? = null,
    )

    @Serializable
    data class Video(
        val id: String? = null,
        val owner: User? = null,
        val game: Game? = null,
        val title: String? = null,
        val publishedAt: String? = null,
        val previewThumbnailURL: String? = null,
        val viewCount: Int? = null,
        val lengthSeconds: Int? = null,
        val contentTags: List<Tag>? = null,
        val animatedPreviewURL: String? = null,
    )

    @Serializable
    data class User(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
        val profileImageURL: String? = null,
    )

    @Serializable
    data class Game(
        val id: String? = null,
        val displayName: String? = null,
    )

    @Serializable
    data class Tag(
        val id: String? = null,
        val localizedName: String? = null,
    )
}