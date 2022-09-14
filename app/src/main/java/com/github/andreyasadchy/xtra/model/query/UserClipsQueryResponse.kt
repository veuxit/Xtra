package com.github.andreyasadchy.xtra.model.query

import com.github.andreyasadchy.xtra.model.helix.clip.Clip

data class UserClipsQueryResponse(val data: List<Clip>, val cursor: String?, val hasNextPage: Boolean?)