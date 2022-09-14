package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.game.Game

data class SearchGamesQueryResponse(val data: List<Game>, val cursor: String?, val hasNextPage: Boolean?)