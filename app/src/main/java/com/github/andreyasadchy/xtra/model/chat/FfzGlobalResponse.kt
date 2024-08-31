package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FfzGlobalResponse(
    @SerialName("default_sets")
    val globalSets: List<Int>,
    val sets: Map<String, FfzResponse>,
)