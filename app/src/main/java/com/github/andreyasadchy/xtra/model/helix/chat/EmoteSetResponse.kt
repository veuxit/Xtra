package com.github.andreyasadchy.xtra.model.helix.chat

class EmoteSetResponse(val data: List<EmoteTemplate>) {

    data class EmoteTemplate(
        val template: String,
        val id: String,
        val formats: List<String>,
        val themes: List<String>,
        val scales: List<String>,
        val name: String,
        val setId: String?,
        val ownerId: String?)
}