package com.github.andreyasadchy.xtra.model.chat

import kotlinx.serialization.Serializable

@Serializable
data class RecentMessagesResponse(
    val messages: List<String>
)