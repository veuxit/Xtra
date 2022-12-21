package com.github.andreyasadchy.xtra.model.gql.channel

import com.github.andreyasadchy.xtra.model.ui.Video

data class ChannelVideosDataResponse(val data: List<Video>, val cursor: String?, val hasNextPage: Boolean?)