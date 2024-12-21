package com.github.andreyasadchy.xtra.model.misc

import kotlinx.serialization.Serializable

@Serializable
class StvGlobalResponse(
    val emotes: List<StvResponse>
)