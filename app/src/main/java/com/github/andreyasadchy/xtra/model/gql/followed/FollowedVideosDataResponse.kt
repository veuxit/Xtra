package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.ui.Video

data class FollowedVideosDataResponse(val data: List<Video>, val cursor: String?, val hasNextPage: Boolean?)