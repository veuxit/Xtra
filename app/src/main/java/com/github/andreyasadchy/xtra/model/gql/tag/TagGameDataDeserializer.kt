package com.github.andreyasadchy.xtra.model.gql.tag

import com.github.andreyasadchy.xtra.model.ui.Tag
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class TagGameDataDeserializer : JsonDeserializer<TagGameDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): TagGameDataResponse {
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("errors")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { if (it == "failed integrity check") throw Exception(it) }
        }
        val data = mutableListOf<Tag>()
        val dataJson = json.takeIf { it.isJsonObject }?.asJsonObject?.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("searchCategoryTags")?.takeIf { it.isJsonArray }?.asJsonArray
        dataJson?.forEach { item ->
            item.takeIf { it.isJsonObject }?.asJsonObject?.let { obj ->
                data.add(Tag(
                    id = obj.get("id")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    name = obj.get("localizedName")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                    scope = obj.get("scope")?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString,
                ))
            }
        }
        return TagGameDataResponse(data)
    }
}
