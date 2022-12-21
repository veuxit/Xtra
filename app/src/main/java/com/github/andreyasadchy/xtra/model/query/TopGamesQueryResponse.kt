package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Game

data class TopGamesQueryResponse(val data: List<Game>, val cursor: String?, val hasNextPage: Boolean?)