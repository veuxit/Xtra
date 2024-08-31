package com.github.andreyasadchy.xtra.model.helix.channel

import com.github.andreyasadchy.xtra.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
data class ChannelSearchResponse(
    val data: List<ChannelSearch>,
    val pagination: Pagination? = null,
)