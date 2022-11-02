package com.github.andreyasadchy.xtra.model.chat

data class TwitchBadge(
    val setId: String,
    val version: String,
    val url1x: String?,
    val url2x: String?,
    val url3x: String?,
    val url4x: String?,
    val title: String? = null)
