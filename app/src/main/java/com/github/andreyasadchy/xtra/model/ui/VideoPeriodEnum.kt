package com.github.andreyasadchy.xtra.model.ui

enum class VideoPeriodEnum(val value: String) {
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    ALL("all");

    override fun toString() = value
}