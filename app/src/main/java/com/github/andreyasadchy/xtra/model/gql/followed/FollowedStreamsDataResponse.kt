package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.ui.Stream

data class FollowedStreamsDataResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)