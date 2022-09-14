package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.follows.Follow

data class UsersLastBroadcastQueryResponse(val data: List<Follow>)