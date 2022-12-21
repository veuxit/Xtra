package com.github.andreyasadchy.xtra.model.ui

enum class BroadcastTypeEnum(val value: String) {
    ALL("all"),
    ARCHIVE("archive"),
    HIGHLIGHT("highlight"),
    UPLOAD("upload");

    override fun toString() = value
}