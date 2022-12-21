package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.ui.Game

data class GameDataResponse(val data: List<Game>, val cursor: String?, val hasNextPage: Boolean?)