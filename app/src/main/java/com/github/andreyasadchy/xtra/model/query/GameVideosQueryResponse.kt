package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Video

data class GameVideosQueryResponse(val data: List<Video>, val cursor: String?, val hasNextPage: Boolean?)