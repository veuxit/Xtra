package com.github.andreyasadchy.xtra.model.helix.chat

import kotlinx.serialization.Serializable

@Serializable
data class EmoteSetsResponse(
    val template: String,
    val data: List<EmoteTemplate>,
)