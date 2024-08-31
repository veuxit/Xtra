package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.Serializable

@Serializable
data class BttvResponse(
    val id: String? = null,
    val code: String? = null,
    val animated: Boolean? = null,
)