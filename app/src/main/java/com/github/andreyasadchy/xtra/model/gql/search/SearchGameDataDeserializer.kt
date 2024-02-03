package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.ui.Game
import com.github.andreyasadchy.xtra.model.ui.Tag
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class SearchGameDataDeserializer : JsonDeserializer<SearchGameDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): SearchGameDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<Game>()
        val dataJson = json.asJsonObject.get("data").asJsonObject.get("searchFor").asJsonObject.get("games").asJsonObject
        val cursor = dataJson?.get("cursor")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString
        dataJson.get("edges").asJsonArray.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("item")?.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                val tags = mutableListOf<Tag>()
                obj.get("tags")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { tagElement ->
                    tagElement.takeIf { it.isJsonObject }?.asJsonObject?.let { tag ->
                        tags.add(Tag(
                            id = tag.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                            name = tag.get("localizedName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                        ))
                    }
                }
                data.add(Game(
                    gameId = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    gameSlug = obj.get("slug")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    gameName = obj.get("displayName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    boxArtUrl = obj.get("boxArtURL")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    viewersCount = obj.get("viewersCount")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isNumber }?.asInt ?: 0, // returns null if 0
                    tags = tags
                ))
            }
        }
        return SearchGameDataResponse(data, cursor)
    }
}
