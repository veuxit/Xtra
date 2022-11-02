package com.github.andreyasadchy.xtra.model.chat

class BttvEmote(
        val id: String,
        override val name: String,
        override val url1x: String? = "https://cdn.betterttv.net/emote/$id/1x",
        override val url2x: String? = "https://cdn.betterttv.net/emote/$id/2x",
        override val url3x: String? = "https://cdn.betterttv.net/emote/$id/2x",
        override val url4x: String? = "https://cdn.betterttv.net/emote/$id/3x",
        override val type: String?) : Emote() {

        private val zeroWidthList = listOf("IceCold", "SoSnowy", "SantaHat", "TopHat", "CandyCane", "ReinDeer", "cvHazmat", "cvMask")
        override val isZeroWidth: Boolean = zeroWidthList.contains(name)
}