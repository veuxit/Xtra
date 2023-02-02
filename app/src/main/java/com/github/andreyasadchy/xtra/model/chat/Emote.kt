package com.github.andreyasadchy.xtra.model.chat

abstract class Emote {
    abstract val name: String
    abstract val url1x: String?
    abstract val url2x: String?
    abstract val url3x: String?
    abstract val url4x: String?
    open val type: String?
        get() = null
    open val isAnimated: Boolean?
        get() = null
    open val isZeroWidth: Boolean
        get() = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Emote || name != other.name) return false
        return true
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun toString(): String {
        return ":$name"
    }
}