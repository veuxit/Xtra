package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VideoMessagesDownloadDataDeserializer : JsonDeserializer<VideoMessagesDownloadDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideoMessagesDownloadDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<JsonObject>()
        val words = mutableListOf<String>()
        val emotes = mutableListOf<String>()
        val badges = mutableListOf<Badge>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("video").asJsonObject.get("comments").asJsonObject.get("edges").asJsonArray
        val lastOffsetSeconds = dataJson.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.get("contentOffsetSeconds")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt
        val cursor = dataJson?.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        val hasNextPage = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("comments")?.takeIf { it.isJsonObject }?.asJsonObject?.get("pageInfo")?.takeIf { it.isJsonObject }?.asJsonObject?.get("hasNextPage")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                val message = StringBuilder()
                obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("fragments")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { fragmentElement ->
                    fragmentElement.takeIf { it.isJsonObject }?.asJsonObject?.let { fragment ->
                        fragment.get("text")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { text ->
                            fragment.get("emote")?.takeIf { it.isJsonObject }?.asJsonObject?.let { emote ->
                                emote.get("emoteID")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { id ->
                                    if (!emotes.contains(id)) {
                                        emotes.add(id)
                                    }
                                }
                            }
                            message.append(text)
                        }
                    }
                }
                obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("userBadges")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { badgeElement ->
                    badgeElement.takeIf { it.isJsonObject }?.asJsonObject?.let { badgeObject ->
                        badgeObject.get("setID")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { setId ->
                            badgeObject.get("version")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { version ->
                                val badge = Badge(
                                    setId = setId,
                                    version = version,
                                )
                                if (!badges.contains(badge)) {
                                    badges.add(badge)
                                }
                            }
                        }
                    }
                }
                message.toString().split(" ").forEach {
                    if (!words.contains(it)) {
                        words.add(it)
                    }
                }
                data.add(obj)
            }
        }
        return VideoMessagesDownloadDataResponse(data, words, emotes, badges, lastOffsetSeconds, cursor, hasNextPage)
    }
}
