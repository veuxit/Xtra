package com.github.andreyasadchy.xtra.model.helix.chat

import com.google.gson.JsonObject

class CheerEmotesResponse(val data: List<CheerTemplate>) {

    data class CheerTemplate(
        val name: String,
        val static: JsonObject?,
        val animated: JsonObject?,
        val minBits: Int,
        val color: String?)
}