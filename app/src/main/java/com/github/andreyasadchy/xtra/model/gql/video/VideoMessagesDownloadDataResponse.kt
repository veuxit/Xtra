package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.chat.Badge
import com.google.gson.JsonObject

data class VideoMessagesDownloadDataResponse(val data: List<JsonObject>, val words: List<String>, val emotes: List<String>, val badges: List<Badge>, val lastOffsetSeconds: Int?, val cursor: String?, val hasNextPage: Boolean?)