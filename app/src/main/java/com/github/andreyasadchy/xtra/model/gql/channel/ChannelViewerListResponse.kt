package com.github.andreyasadchy.xtra.model.gql.channel

import com.github.andreyasadchy.xtra.model.gql.Error
import kotlinx.serialization.Serializable

@Serializable
data class ChannelViewerListResponse(
    val errors: List<Error>? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val user: User,
    )

    @Serializable
    data class User(
        val channel: Channel,
    )

    @Serializable
    data class Channel(
        val chatters: Chatters,
    )

    @Serializable
    data class Chatters(
        val broadcasters: List<Chatter>? = null,
        val moderators: List<Chatter>? = null,
        val vips: List<Chatter>? = null,
        val viewers: List<Chatter>? = null,
        val count: Int? = null,
    )

    @Serializable
    data class Chatter(
        val login: String? = null,
    )
}