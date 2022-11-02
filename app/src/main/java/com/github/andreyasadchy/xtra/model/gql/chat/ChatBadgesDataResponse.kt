package com.github.andreyasadchy.xtra.model.gql.chat

import com.github.andreyasadchy.xtra.model.chat.TwitchBadge

data class ChatBadgesDataResponse(val global: List<TwitchBadge>, val channel: List<TwitchBadge>)