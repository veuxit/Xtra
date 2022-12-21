package com.github.andreyasadchy.xtra.model.ui

enum class StreamSortEnum(val value: String) {
    RECOMMENDED("RELEVANCE"),
    VIEWERS_HIGH("VIEWER_COUNT"),
    VIEWERS_LOW("VIEWER_COUNT_ASC"),
    RECENT("RECENT");

    override fun toString() = value
}