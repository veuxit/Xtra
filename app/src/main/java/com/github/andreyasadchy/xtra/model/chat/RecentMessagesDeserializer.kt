package com.github.andreyasadchy.xtra.model.chat

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class RecentMessagesDeserializer : JsonDeserializer<RecentMessagesResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RecentMessagesResponse {
        val messages = mutableListOf<String>()
        json.takeIf { it.isJsonObject }?.asJsonObject?.get("messages")?.takeIf { it.isJsonArray }?.asJsonArray?.forEach { element ->
            element.takeIf { it.isJsonPrimitive }?.asJsonPrimitive?.takeIf { it.isString }?.asString?.let { message ->
                messages.add(message)
            }
        }
        return RecentMessagesResponse(messages)
    }
}
