package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.follows.Follow

data class FollowedUsersQueryResponse(val data: List<Follow>, val cursor: String?, val hasNextPage: Boolean?)