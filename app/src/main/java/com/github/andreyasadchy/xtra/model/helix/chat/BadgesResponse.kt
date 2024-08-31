package com.github.andreyasadchy.xtra.model.helix.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BadgesResponse(
    val data: List<Set>,
) {
    @Serializable
    data class Set(
        @SerialName("set_id")
        val setId: String? = null,
        val versions: List<Version>? = null,
    )

    @Serializable
    data class Version(
        val id: String? = null,
        @SerialName("image_url_1x")
        val url1x: String? = null,
        @SerialName("image_url_2x")
        val url2x: String? = null,
        @SerialName("image_url_4x")
        val url4x: String? = null,
    )
}