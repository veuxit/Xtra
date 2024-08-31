package com.github.andreyasadchy.xtra.model.helix

import kotlinx.serialization.Serializable

@Serializable
data class Pagination(
    val cursor: String? = null,
)