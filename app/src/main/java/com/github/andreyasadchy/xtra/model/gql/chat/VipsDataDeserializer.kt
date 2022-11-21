package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.helix.user.User
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import java.lang.reflect.Type

class VipsDataDeserializer : JsonDeserializer<VipsDataResponse> {

    @Throws(JsonParseException::class)
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): VipsDataResponse {
        val data = mutableListOf<User>()
        val dataJson = json.asJsonObject?.getAsJsonObject("data")?.getAsJsonObject("user")?.getAsJsonObject("vips")?.getAsJsonArray("edges")
        dataJson?.forEach { element ->
            element?.asJsonObject?.let { obj ->
                obj.getAsJsonObject("node")?.let { node ->
                    data.add(User(
                        id = node.get("id")?.takeIf { !it.isJsonNull }?.asString,
                        login = node.get("login")?.takeIf { !it.isJsonNull }?.asString
                    ))
                }
            }
        }
        return VipsDataResponse(data)
    }
}
