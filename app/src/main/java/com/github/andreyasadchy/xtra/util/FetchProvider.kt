package com.github.andreyasadchy.xtra.util

import com.tonyodev.fetch2.Fetch
import com.tonyodev.fetch2.FetchConfiguration
import javax.inject.Inject

class FetchProvider @Inject constructor(
        private val configurationBuilder: FetchConfiguration.Builder) {

    private var instance: Fetch? = null

    fun get(videoId: Int? = null, concurrentDownloads: Int = 10): Fetch {
        if (instance == null || instance!!.isClosed) {
            instance = Fetch.getInstance(
                configurationBuilder
                    .setDownloadConcurrentLimit(concurrentDownloads)
                    .setNamespace("Fetch #$videoId")
                    .build())
        }
        return instance!!
    }
}