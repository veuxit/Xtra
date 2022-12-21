package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Stream

data class UserFollowedStreamsQueryResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)