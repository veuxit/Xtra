package com.github.andreyasadchy.xtra.model.chat

data class TwitchBadge(
    val setId: String,
    val version: String,
    val url: String,
    val title: String? = null)
