package com.github.andreyasadchy.xtra.model.chat

class FfzEmote(
        override val name: String,
        override val url1x: String?,
        override val url2x: String?,
        override val url3x: String?,
        override val url4x: String?,
        override val type: String?,
        override val isAnimated: Boolean?) : Emote()