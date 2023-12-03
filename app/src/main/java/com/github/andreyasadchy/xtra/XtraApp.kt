package com.github.andreyasadchy.xtra

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import androidx.work.Configuration
import com.github.andreyasadchy.xtra.di.AppInjector
import com.github.andreyasadchy.xtra.util.AppLifecycleObserver
import com.github.andreyasadchy.xtra.util.C
import com.github.andreyasadchy.xtra.util.LifecycleListener
import com.github.andreyasadchy.xtra.util.TlsSocketFactory
import com.github.andreyasadchy.xtra.util.prefs
import com.github.andreyasadchy.xtra.util.toast
import dagger.hilt.android.HiltAndroidApp
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import java.security.KeyStore
import java.security.Security
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


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
        } catch (e: VerifyError) {
            if (prefs().getBoolean(C.DEBUG_SHOW_CONSCRYPT_ERROR_TOAST, true)) {
                toast("Conscrypt VerifyError")
            }
        }
        val trustManager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
            init(null as KeyStore?)
            trustManagers.first { it is X509TrustManager } as X509TrustManager
        }
        val sslContext = SSLContext.getInstance(TlsVersion.TLS_1_2.javaName())
        sslContext.init(null, arrayOf(trustManager), null)
        HttpsURLConnection.setDefaultSSLSocketFactory(TlsSocketFactory(sslContext.socketFactory)) // enable TLS 1.2 for exoplayer

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
