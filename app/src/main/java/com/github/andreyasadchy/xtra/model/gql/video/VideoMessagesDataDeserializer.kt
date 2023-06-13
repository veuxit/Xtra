package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.github.andreyasadchy.xtra.model.chat.TwitchEmote
import com.github.andreyasadchy.xtra.model.chat.VideoChatMessage
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VideoMessagesDataDeserializer : JsonDeserializer<VideoMessagesDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VideoMessagesDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<VideoChatMessage>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("video").asJsonObject.get("comments").asJsonObject.get("edges").asJsonArray
        val cursor = dataJson?.lastOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        val hasNextPage = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("video")?.takeIf { it.isJsonObject }?.asJsonObject?.get("comments")?.takeIf { it.isJsonObject }?.asJsonObject?.get("pageInfo")?.takeIf { it.isJsonObject }?.asJsonObject?.get("hasNextPage")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isBoolean }?.asBoolean
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("node")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                val message = StringBuilder()
                val emotes = mutableListOf<TwitchEmote>()
                obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("fragments")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { fragmentElement ->
                    fragmentElement.takeIf { it.isJsonObject }?.asJsonObject?.let { fragment ->
                        fragment.get("text")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { text ->
                            fragment.get("emote")?.takeIf { it.isJsonObject }?.asJsonObject?.let { emote ->
                                emote.get("emoteID")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { id ->
                                    emotes.add(TwitchEmote(
                                        id = id,
                                        begin = message.codePointCount(0, message.length),
                                        end = message.codePointCount(0, message.length) + text.lastIndex
                                    ))
                                }
                            }
                            message.append(text)
                        }
                    }
                }
                val badges = mutableListOf<Badge>()
                obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("userBadges")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { badgeElement ->
                    badgeElement.takeIf { it.isJsonObject }?.asJsonObject?.let { badge ->
                        badge.get("setID")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { setId ->
                            badge.get("version")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { version ->
                                badges.add(Badge(
                                    setId = setId,
                                    version = version,
                                ))
                            }
                        }
                    }
                }
                data.add(VideoChatMessage(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    offsetSeconds = obj.get("contentOffsetSeconds")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt,
                    userId = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    userLogin = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    userName = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    message = message.toString(),
                    color = obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("userColor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    emotes = emotes,
                    badges = badges,
                    fullMsg = item.toString()
                ))
            }
        }
        return VideoMessagesDataResponse(data, cursor, hasNextPage)
    }
}
