package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.Serializable

@Serializable
data class StvResponse(
    val name: String? = null,
    val flags: Int? = null,
    val data: Data? = null,
) {
    @Serializable
    data class Data(
        val host: Host? = null,
        val animated: Boolean? = null,
    )

    @Serializable
    data class Host(
        val url: String? = null,
        val files: List<File>? = null,
    )

    @Serializable
    data class File(
        val name: String? = null,
    )
}