package com.github.andreyasadchy.xtra.model.helix.user

import kotlinx.serialization.Serializable

@Serializable
data class UsersResponse(
    val data: List<User>,
)