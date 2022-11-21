package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.helix.user.User

data class ModeratorsDataResponse(val data: List<User>)