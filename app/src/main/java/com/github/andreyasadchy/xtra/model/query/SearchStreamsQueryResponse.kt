package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.ui.Stream

data class SearchStreamsQueryResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)