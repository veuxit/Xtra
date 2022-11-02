package com.github.andreyasadchy.xtra.model.gql.video

import com.github.andreyasadchy.xtra.model.chat.VideoChatMessage

data class VideoMessagesDataResponse(val data: List<VideoChatMessage>, val cursor: String?, val hasNextPage: Boolean?)