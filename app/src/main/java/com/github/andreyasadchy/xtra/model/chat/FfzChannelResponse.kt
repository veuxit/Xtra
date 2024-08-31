package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.Serializable

@Serializable
data class FfzChannelResponse(
    val sets: Map<String, FfzResponse>,
)