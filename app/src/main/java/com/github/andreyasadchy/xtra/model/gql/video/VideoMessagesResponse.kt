package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.gql.Error
import com.github.andreyasadchy.xtra.model.gql.PageInfo
import kotlinx.serialization.Serializable

@Serializable
data class VideoMessagesResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val video: Video,
    )

    @Serializable
    data class Video(
        val comments: Comments,
    )

    @Serializable
    data class Comments(
        val edges: List<Item>,
        val pageInfo: PageInfo? = null,
    )

    @Serializable
    data class Item(
        val node: Comment,
        val cursor: String? = null,
    )

    @Serializable
    data class Comment(
        val id: String? = null,
        val contentOffsetSeconds: Int? = null,
        val message: Message? = null,
        val commenter: Commenter? = null,
    )

    @Serializable
    data class Message(
        val fragments: List<Fragment>? = null,
        val userBadges: List<Badge>? = null,
        val userColor: String? = null,
    )

    @Serializable
    data class Fragment(
        val text: String? = null,
        val emote: Emote? = null,
    )

    @Serializable
    data class Emote(
        val emoteID: String? = null,
    )

    @Serializable
    data class Badge(
        val setID: String? = null,
        val version: String? = null,
    )

    @Serializable
    data class Commenter(
        val id: String? = null,
        val login: String? = null,
        val displayName: String? = null,
    )
}