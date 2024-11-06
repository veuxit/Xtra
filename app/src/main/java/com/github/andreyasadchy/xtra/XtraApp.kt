package com.github.andreyasadchy.xtra

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import androidx.work.Configuration
import com.github.andreyasadchy.xtra.di.AppInjector
import com.github.andreyasadchy.xtra.util.AppLifecycleObserver
import com.github.andreyasadchy.xtra.util.LifecycleListener
import dagger.hilt.android.HiltAndroidApp
import org.conscrypt.Conscrypt
import java.security.Security
import javax.inject.Inject


@HiltAndroidApp
class XtraApp : Application(), Configuration.Provider {

    companion object {
        lateinit var INSTANCE: Application
    }

    private val appLifecycleObserver = AppLifecycleObserver()

    override fun onCreate() {
        super.onCreate()
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (e: Exception) {

        }

        INSTANCE = this
        AppInjector.init(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        if (BuildConfig.DEBUG) {
            MultiDex.install(this)
        }
    }

    fun addLifecycleListener(listener: LifecycleListener) {
        appLifecycleObserver.addListener(listener)
    }

    fun removeLifecycleListener(listener: LifecycleListener) {
        appLifecycleObserver.removeListener(listener)
    }

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
