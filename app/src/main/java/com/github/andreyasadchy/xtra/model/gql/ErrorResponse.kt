package com.github.andreyasadchy.xtra.model.gql

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val errors: List<Error>? = null,
)