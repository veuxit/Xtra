package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.User

data class SearchChannelsQueryResponse(val data: List<User>, val cursor: String?, val hasNextPage: Boolean?)