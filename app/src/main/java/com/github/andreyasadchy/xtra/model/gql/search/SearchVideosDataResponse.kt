package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.ui.Video

data class SearchVideosDataResponse(val data: List<Video>, val cursor: String?)