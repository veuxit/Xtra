package com.github.andreyasadchy.xtra.model.helix.stream

import com.github.andreyasadchy.xtra.model.ui.Stream

data class StreamsResponse(val data: List<Stream>, val cursor: String?)