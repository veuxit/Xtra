package com.github.andreyasadchy.xtra.model.gql.stream

import com.github.andreyasadchy.xtra.model.ui.Stream
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class ViewersDataDeserializer : JsonDeserializer<Stream> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Stream {
        val obj = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("stream")
        return Stream(
            id = obj?.get("id")?.takeIf { !it.isJsonNull }?.asString,
            viewerCount = obj?.getAsJsonPrimitive("viewersCount")?.asInt
        )
    }
}
