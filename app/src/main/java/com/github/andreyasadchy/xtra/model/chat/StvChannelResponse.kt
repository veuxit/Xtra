package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StvChannelResponse(
    @SerialName("emote_set")
    val emoteSet: StvGlobalResponse
)