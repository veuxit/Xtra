package com.github.andreyasadchy.xtra.model.gql.stream

import com.github.andreyasadchy.xtra.model.ui.Stream

data class StreamsDataResponse(val data: List<Stream>, val cursor: String?, val hasNextPage: Boolean?)