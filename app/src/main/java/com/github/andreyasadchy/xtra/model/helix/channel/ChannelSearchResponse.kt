package com.github.andreyasadchy.xtra.model.helix.channel

import com.github.andreyasadchy.xtra.model.ui.User

data class ChannelSearchResponse(val data: List<User>, val cursor: String?)