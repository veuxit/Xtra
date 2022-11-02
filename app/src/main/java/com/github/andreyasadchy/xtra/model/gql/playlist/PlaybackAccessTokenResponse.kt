package com.github.andreyasadchy.xtra.model.gql.playlist

import com.github.andreyasadchy.xtra.model.PlaybackAccessToken

data class PlaybackAccessTokenResponse(val streamToken: PlaybackAccessToken?, val videoToken: PlaybackAccessToken?)