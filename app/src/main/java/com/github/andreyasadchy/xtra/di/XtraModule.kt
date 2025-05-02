package com.github.andreyasadchy.xtra.di

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.OptIn
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import org.chromium.net.CronetEngine
import org.chromium.net.CronetProvider
import org.chromium.net.QuicOptions
import org.chromium.net.RequestFinishedInfo
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class XtraModule {

    @Singleton
    @Provides
    @OptIn(QuicOptions.Experimental::class)
    fun providesCronetEngine(application: Application): CronetEngine? {
        return if (CronetProvider.getAllProviders(application).any { it.isEnabled }) {
            CronetEngine.Builder(application).apply {
                val userAgent = "Cronet/" + defaultUserAgent.substringAfter("Cronet/", "").substringBefore(')')
                setUserAgent(userAgent)
                setQuicOptions(QuicOptions.builder().setHandshakeUserAgent(userAgent).build())
                addQuicHint("gql.twitch.tv", 443, 443)
                addQuicHint("www.twitch.tv", 443, 443)
                addQuicHint("7tv.io", 443, 443)
                addQuicHint("api.betterttv.net", 443, 443)
            }.build().also {
                if (BuildConfig.DEBUG) {
                    it.addRequestFinishedListener(object : RequestFinishedInfo.Listener(Executors.newSingleThreadExecutor()) {
                        override fun onRequestFinished(requestInfo: RequestFinishedInfo) {
                            requestInfo.responseInfo?.let {
                                Log.i("Cronet", "${it.httpStatusCode} ${it.negotiatedProtocol} ${it.url}")
                                it.allHeadersAsList?.forEach {
                                    Log.i("Cronet", "${it.key}: ${it.value}")
                                }
                            }
                        }
                    })
                }
            }
        } else {
            null
        }
    }

    @Singleton
    @Provides
    fun providesCronetExecutor(): ExecutorService {
        return Executors.newCachedThreadPool()
    }

    @Singleton
    @Provides
    fun providesOkHttpClient(application: Application): OkHttpClient {
        val builder = OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                try {
                    val certificate = application.resources.openRawResource(R.raw.isrgrootx1).bufferedReader().use {
                        it.readText()
                    }.decodeCertificatePem()
                    val certificates = HandshakeCertificates.Builder()
                        .addTrustedCertificate(certificate)
                        .addPlatformTrustedCertificates()
                        .build()
                    sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager)
                } catch (e: Exception) {

                }
            }
            connectTimeout(5, TimeUnit.MINUTES)
            writeTimeout(5, TimeUnit.MINUTES)
            readTimeout(5, TimeUnit.MINUTES)
        }
        return builder.build()
    }

    @Singleton
    @Provides
    fun providesJsonInstance(): Json {
        return Json { ignoreUnknownKeys = true }
    }
}
