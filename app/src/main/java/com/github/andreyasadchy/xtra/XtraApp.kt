package com.github.andreyasadchy.xtra

import android.app.Application
import android.content.Context
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.multidex.MultiDex
import com.github.andreyasadchy.xtra.di.AppInjector
import com.github.andreyasadchy.xtra.util.*
import dagger.hilt.android.HiltAndroidApp
import okhttp3.TlsVersion
import org.conscrypt.Conscrypt
import java.security.KeyStore
import java.security.Security
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


@HiltAndroidApp
class XtraApp : Application() {

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
}
