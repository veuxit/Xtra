package com.github.andreyasadchy.xtra

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.github.andreyasadchy.xtra.util.coil.CacheControlCacheStrategy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject


@HiltAndroidApp
class XtraApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    companion object {
        lateinit var INSTANCE: Application
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context).apply {
            if (BuildConfig.DEBUG) {
                logger(DebugLogger())
            }
            components {
                add(OkHttpNetworkFetcherFactory(
                    callFactory = { okHttpClient },
                    cacheStrategy = { CacheControlCacheStrategy() }
                ))
            }
        }.build()
    }
}
