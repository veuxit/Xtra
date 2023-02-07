package com.github.andreyasadchy.xtra.model.gql.tag

import com.github.andreyasadchy.xtra.model.ui.Tag
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class FreeformTagDataDeserializer : JsonDeserializer<FreeformTagDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): FreeformTagDataResponse {
        val data = mutableListOf<Tag>()
        val dataJson = json.asJsonObject.getAsJsonObject("data").getAsJsonObject("searchFreeformTags").getAsJsonArray("edges")
        dataJson.forEach { item ->
            item.asJsonObject.getAsJsonObject("node").let { obj ->
                data.add(Tag(
                    name = obj.get("tagName").takeIf { !it.isJsonNull }?.asString,
                ))
            }
        }
        return FreeformTagDataResponse(data)
    }
}
