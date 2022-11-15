package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.game.Game

data class UserFollowedGamesQueryResponse(val data: List<Game>)