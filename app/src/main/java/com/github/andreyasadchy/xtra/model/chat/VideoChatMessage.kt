package com.github.andreyasadchy.xtra.model.chat

data class VideoChatMessage(
    override val id: String?,
    val offsetSeconds: Int?,
    override val userId: String?,
    override val userLogin: String?,
    override val userName: String?,
    override val message: String?,
    override val color: String?,
    override val isAction: Boolean = false,
    override val emotes: List<TwitchEmote>?,
    override val badges: List<Badge>?) : ChatMessage {

    override val fullMsg: String
        get() = this.toString()
}