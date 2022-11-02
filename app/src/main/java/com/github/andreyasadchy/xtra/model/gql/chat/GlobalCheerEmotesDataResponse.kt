package com.github.andreyasadchy.xtra.model.gql.chat

import com.google.gson.JsonArray

data class GlobalCheerEmotesDataResponse(val config: CheerConfig, val tiers: List<CheerTier>) {

    data class CheerConfig(
        val backgrounds: JsonArray,
        val colors: JsonArray,
        val scales: JsonArray,
        val types: JsonArray)

    data class CheerTier(
        val template: String,
        val prefix: String,
        val tierBits: Int)
}