package com.github.andreyasadchy.xtra.model.helix.chat

import com.github.andreyasadchy.xtra.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
data class UserEmotesResponse(
    val template: String,
    val data: List<EmoteTemplate>,
    val pagination: Pagination? = null,
)