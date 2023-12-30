package com.github.andreyasadchy.xtra.util

fun String.nullIfEmpty() = takeIf { it.isNotEmpty() }

val String?.isLightTheme
    get() = listOf("2", "5").contains(this)