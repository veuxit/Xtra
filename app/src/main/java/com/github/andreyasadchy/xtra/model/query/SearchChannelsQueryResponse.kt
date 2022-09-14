package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.channel.ChannelSearch

data class SearchChannelsQueryResponse(val data: List<ChannelSearch>, val cursor: String?, val hasNextPage: Boolean?)