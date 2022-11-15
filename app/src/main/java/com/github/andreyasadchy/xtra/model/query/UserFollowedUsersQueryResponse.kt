package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.follows.Follow

data class UserFollowedUsersQueryResponse(val data: List<Follow>, val cursor: String?, val hasNextPage: Boolean?)