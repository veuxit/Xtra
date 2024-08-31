package com.github.andreyasadchy.xtra.model.helix.follows

import com.github.andreyasadchy.xtra.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
data class FollowsResponse(
    val data: List<Follow>,
    val pagination: Pagination? = null,
)