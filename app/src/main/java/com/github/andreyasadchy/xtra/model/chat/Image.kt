package com.github.andreyasadchy.xtra.model.chat

data class Image(
        val url1x: String?,
        val url2x: String?,
        val url3x: String?,
        val url4x: String?,
        val type: String? = null,
        val isAnimated: Boolean? = null,
        val isZeroWidth: Boolean = false,
        var start: Int,
        var end: Int,
        val isEmote: Boolean)