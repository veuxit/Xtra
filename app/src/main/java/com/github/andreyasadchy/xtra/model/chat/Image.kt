package com.github.andreyasadchy.xtra.model.chat

data class Image(
        val url1x: String?,
        val url2x: String?,
        val url3x: String?,
        val url4x: String?,
        val type: String?,
        val isZeroWidth: Boolean,
        var start: Int,
        var end: Int,
        val isEmote: Boolean)