package com.github.andreyasadchy.xtra.model.chat

class TwitchEmote(
        val id: String? = null,
        override val name: String = "",
        override val url1x: String? = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/1.0",
        override val url2x: String? = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0",
        override val url3x: String? = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0",
        override val url4x: String? = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/3.0",
        override val type: String? = "gif",
        override val isAnimated: Boolean? = true,
        override val isZeroWidth: Boolean = false,
        var begin: Int = 0,
        var end: Int = 0,
        val setId: String? = null,
        val ownerId: String? = null) : Emote()
