package com.github.andreyasadchy.xtra.model.gql.followed

import com.github.andreyasadchy.xtra.model.ui.User

data class FollowedChannelsDataResponse(val data: List<User>, val cursor: String?, val hasNextPage: Boolean?)