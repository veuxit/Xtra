package com.github.andreyasadchy.xtra.model.gql.game

import com.github.andreyasadchy.xtra.model.ui.Video

data class GameVideosDataResponse(val data: List<Video>, val cursor: String?, val hasNextPage: Boolean?)