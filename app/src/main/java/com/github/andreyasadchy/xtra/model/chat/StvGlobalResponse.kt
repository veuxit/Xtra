package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.Serializable

@Serializable
data class StvGlobalResponse(
    val emotes: List<StvResponse>
)