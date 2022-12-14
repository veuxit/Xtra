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
        val data = mutableListOf<VideoChatMessage>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("video")?.getAsJsonObject("comments")?.getAsJsonArray("edges")
        val cursor = dataJson?.lastOrNull()?.asJsonObject?.get("cursor")?.takeIf { !it.isJsonNull }?.asString
        val hasNextPage = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("video")?.getAsJsonObject("comments")?.get("pageInfo")?.takeIf { !it.isJsonNull }?.asJsonObject?.get("hasNextPage")?.takeIf { !it.isJsonNull }?.asBoolean
        dataJson?.forEach { item ->
            item.asJsonObject.getAsJsonObject("node")?.let { obj ->
                val message = StringBuilder()
                val emotes = mutableListOf<TwitchEmote>()
                obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("fragments")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { fragmentElement ->
                    fragmentElement?.takeIf { it.isJsonObject }?.asJsonObject.let { fragment ->
                        fragment?.get("text")?.takeIf { !it.isJsonNull }?.asString?.let { text ->
                            fragment.get("emote")?.takeIf { it.isJsonObject }?.asJsonObject?.let { emote ->
                                emote.get("emoteID")?.takeIf { !it.isJsonNull }?.asString?.let { id ->
                                    emotes.add(TwitchEmote(
                                        name = id,
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
                    badgeElement?.takeIf { it.isJsonObject }?.asJsonObject.let { badge ->
                        badge?.get("setID")?.takeIf { !it.isJsonNull }?.asString?.let { setId ->
                            badge.get("version")?.takeIf { !it.isJsonNull }?.asString?.let { version ->
                                badges.add(Badge(
                                    setId = setId,
                                    version = version,
                                ))
                            }
                        }
                    }
                }
                data.add(VideoChatMessage(
                    id = obj.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    offsetSeconds = obj.get("contentOffsetSeconds")?.takeIf { !it.isJsonNull }?.asInt,
                    userId = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("id")?.takeIf { !it.isJsonNull }?.asString,
                    userLogin = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("login")?.takeIf { !it.isJsonNull }?.asString,
                    userName = obj.get("commenter")?.takeIf { it.isJsonObject }?.asJsonObject?.get("displayName")?.takeIf { !it.isJsonNull }?.asString,
                    message = message.toString(),
                    color = obj.get("message")?.takeIf { it.isJsonObject }?.asJsonObject?.get("userColor")?.takeIf { !it.isJsonNull }?.asString,
                    emotes = emotes,
                    badges = badges,
                    fullMsg = item.toString()
                ))
            }
        }
        return VideoMessagesDataResponse(data, cursor, hasNextPage)
    }
}
