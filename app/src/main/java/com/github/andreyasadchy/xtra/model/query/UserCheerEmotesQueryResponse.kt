package com.github.andreyasadchy.xtra.model.query

import com.google.gson.JsonArray

data class UserCheerEmotesQueryResponse(val config: CheerConfig, val tiers: List<CheerTier>) {

    data class CheerConfig(
        val backgrounds: JsonArray,
        val colors: List<CheermoteColorConfig>,
        val scales: JsonArray,
        val types: List<CheermoteDisplayType>)

    data class CheermoteColorConfig (
        val bits: Int,
        val color: String)

    data class CheermoteDisplayType (
        val animation: String?,
        val extension: String?)

    data class CheerTier(
        val template: String,
        val prefix: String,
        val tierBits: Int)
}