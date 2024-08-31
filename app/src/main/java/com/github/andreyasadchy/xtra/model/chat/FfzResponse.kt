package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FfzResponse(
    val emoticons: List<Emote>? = null,
) {
    @Serializable
    data class Emote(
        val name: String? = null,
        val animated: Urls? = null,
        val urls: Urls? = null,
    )

    @Serializable
    data class Urls(
        @SerialName("1")
        val url1x: String? = null,
        @SerialName("2")
        val url2x: String? = null,
        @SerialName("4")
        val url4x: String? = null,
    )
}