package com.github.andreyasadchy.xtra.model.helix.chat

import com.google.gson.JsonArray

class EmoteSetResponse(val data: List<EmoteTemplate>) {

    data class EmoteTemplate(
        val template: String,
        val id: String,
        val format: JsonArray,
        val theme: JsonArray,
        val scale: JsonArray,
        val name: String,
        val setId: String? = null,
        val ownerId: String? = null)
}