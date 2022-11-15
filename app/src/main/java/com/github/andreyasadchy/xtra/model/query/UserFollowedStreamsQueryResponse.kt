package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.stream.Stream

data class UserFollowedStreamsQueryResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)