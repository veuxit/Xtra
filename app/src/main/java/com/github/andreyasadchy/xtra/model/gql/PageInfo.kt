package com.github.andreyasadchy.xtra.model.gql

import kotlinx.serialization.Serializable

@Serializable
data class PageInfo(
    val hasNextPage: Boolean? = null,
)