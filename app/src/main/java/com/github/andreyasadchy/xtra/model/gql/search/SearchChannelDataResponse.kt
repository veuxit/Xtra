package com.github.andreyasadchy.xtra.model.gql.search

import com.github.andreyasadchy.xtra.model.ui.User

data class SearchChannelDataResponse(val data: List<User>, val cursor: String?)