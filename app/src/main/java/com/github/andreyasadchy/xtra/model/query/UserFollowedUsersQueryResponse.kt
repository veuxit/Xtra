package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.User

data class UserFollowedUsersQueryResponse(val data: List<User>, val cursor: String?, val hasNextPage: Boolean?)