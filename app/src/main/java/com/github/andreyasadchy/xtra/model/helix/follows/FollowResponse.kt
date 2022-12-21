package com.github.andreyasadchy.xtra.model.helix.follows

import com.github.andreyasadchy.xtra.model.ui.User

data class FollowResponse(val data: List<User>, val total: Int?, val cursor: String?)