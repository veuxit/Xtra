package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class StvChannelDeserializer : JsonDeserializer<StvChannelResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): StvChannelResponse {
        val emotes = mutableListOf<StvEmote>()
        val dataJson = json.asJsonObject.getAsJsonObject("emote_set").get("emotes").takeIf { it?.isJsonArray == true }?.asJsonArray
        dataJson?.forEach { emote ->
            emote.asJsonObject.let { obj ->
                obj.get("name").asString.let { name ->
                    val urls = mutableListOf<String>()
                    val host = obj.getAsJsonObject("data").getAsJsonObject("host")
                    val template = host.get("url").asString
                    host.getAsJsonArray("files").forEach { file ->
                        file.asJsonObject.let { obj ->
                            obj.get("name").asString.let { name ->
                                if (!name.contains("avif", true)) {
                                    urls.add("https:${template}/${name}")
                                }
                            }
                        }
                    }
                    emotes.add(StvEmote(
                        name = name,
                        url1x = if (urls.size >= 1) urls[0] else "https:${template}/1x.webp",
                        url2x = if (urls.size >= 2) urls[1] else if (urls.isEmpty()) "https:${template}/2x.webp" else null,
                        url3x = if (urls.size >= 3) urls[2] else if (urls.isEmpty()) "https:${template}/3x.webp" else null,
                        url4x = if (urls.size >= 4) urls[3] else if (urls.isEmpty()) "https:${template}/4x.webp" else null,
                        type = if (urls.size >= 1) urls[0].substringAfterLast(".").let { "image/$it" } else "image/webp",
                        isZeroWidth = obj.get("flags")?.takeIf { !it.isJsonNull }?.asInt == 1
                    ))
                }
            }
        }
        return StvChannelResponse(emotes)
    }
}
