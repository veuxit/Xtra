package com.github.andreyasadchy.xtra

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.multidex.MultiDex
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject


@HiltAndroidApp
class XtraApp : Application(), Configuration.Provider {

    companion object {
        lateinit var INSTANCE: Application
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (BuildConfig.DEBUG) {
            MultiDex.install(this)
        }
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
