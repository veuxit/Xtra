package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.ui.Clip

data class GameClipsDataResponse(val data: List<Clip>, val cursor: String?, val hasNextPage: Boolean?)