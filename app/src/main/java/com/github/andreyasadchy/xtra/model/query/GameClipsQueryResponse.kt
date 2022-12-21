package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Clip

data class GameClipsQueryResponse(val data: List<Clip>, val cursor: String?, val hasNextPage: Boolean?)