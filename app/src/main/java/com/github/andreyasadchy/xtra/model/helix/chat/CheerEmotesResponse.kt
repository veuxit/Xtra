package com.github.andreyasadchy.xtra.model.helix.chat

class CheerEmotesResponse(val data: List<CheerTemplate>) {

    data class CheerTemplate(
        val name: String,
        val format: String,
        val theme: String,
        val urls: Map<String, String>,
        val minBits: Int,
        val color: String?)
}