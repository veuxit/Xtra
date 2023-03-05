package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FfzGlobalDeserializer : JsonDeserializer<FfzGlobalResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FfzGlobalResponse {
        val emotes = mutableListOf<FfzEmote>()
        val globalSets = json.asJsonObject.getAsJsonArray("default_sets").map { it.asInt }
        json.asJsonObject.getAsJsonObject("sets").entrySet().forEach { set ->
            if (globalSets.contains(set.key.toIntOrNull())) {
                set.value.asJsonObject.getAsJsonArray("emoticons").forEach { emote ->
                    emote.asJsonObject.let { obj ->
                        obj.get("name")?.asString?.let { name ->
                            val isAnimated = obj.get("animated")?.let { it.isJsonObject && it.asJsonObject.entrySet().isNotEmpty() } == true
                            val urls = obj.getAsJsonObject(if (isAnimated) "animated" else "urls")
                            emotes.add(FfzEmote(
                                name = name,
                                url1x = urls.get("1")?.takeIf { !it.isJsonNull }?.asString,
                                url2x = urls.get("2")?.takeIf { !it.isJsonNull }?.asString,
                                url3x = urls.get("2")?.takeIf { !it.isJsonNull }?.asString,
                                url4x = urls.get("4")?.takeIf { !it.isJsonNull }?.asString,
                                type = if (isAnimated) "webp" else null,
                                isAnimated = isAnimated
                            ))
                        }
                    }
                }
            }
        }
        return FfzGlobalResponse(emotes)
    }
}
