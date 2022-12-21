package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.ui.Game

data class SearchGameDataResponse(val data: List<Game>, val cursor: String?)