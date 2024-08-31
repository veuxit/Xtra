package com.github.andreyasadchy.xtra.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidationResponse(
    @SerialName("client_id")
    val clientId: String,
    val login: String? = null,
    @SerialName("user_id")
    val userId: String? = null
)