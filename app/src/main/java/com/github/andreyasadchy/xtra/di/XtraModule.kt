package com.github.andreyasadchy.xtra.di

import android.app.Application
import android.os.Build
import android.util.Log
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.IdApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.api.UsherApi
import com.github.andreyasadchy.xtra.util.TlsSocketFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext

@Module
@InstallIn(SingletonComponent::class)
class XtraModule {

    @Singleton
    @Provides
    fun providesHelixApi(client: OkHttpClient, jsonConverterFactory: Converter.Factory): HelixApi {
        return Retrofit.Builder()
                .baseUrl("https://api.twitch.tv/helix/")
                .client(client)
                .addConverterFactory(jsonConverterFactory)
                .build()
                .create(HelixApi::class.java)
    }

    @Singleton
    @Provides
    fun providesUsherApi(client: OkHttpClient, jsonConverterFactory: Converter.Factory): UsherApi {
        return Retrofit.Builder()
                .baseUrl("https://usher.ttvnw.net/")
                .client(client)
                .addConverterFactory(jsonConverterFactory)
                .build()
                .create(UsherApi::class.java)
    }

    @Singleton
    @Provides
    fun providesMiscApi(client: OkHttpClient, jsonConverterFactory: Converter.Factory): MiscApi {
        return Retrofit.Builder()
                .baseUrl("https://api.twitch.tv/") //placeholder url
                .client(client)
                .addConverterFactory(jsonConverterFactory)
                .build()
                .create(MiscApi::class.java)
    }

    @Singleton
    @Provides
    fun providesIdApi(client: OkHttpClient, jsonConverterFactory: Converter.Factory): IdApi {
        return Retrofit.Builder()
                .baseUrl("https://id.twitch.tv/oauth2/")
                .client(client)
                .addConverterFactory(jsonConverterFactory)
                .build()
                .create(IdApi::class.java)
    }

    @Singleton
    @Provides
    fun providesGraphQLApi(client: OkHttpClient, jsonConverterFactory: Converter.Factory): GraphQLApi {
        return Retrofit.Builder()
                .baseUrl("https://gql.twitch.tv/gql/")
                .client(client)
                .addConverterFactory(jsonConverterFactory)
                .build()
                .create(GraphQLApi::class.java)
    }

    @Singleton
    @Provides
    fun providesJsonConverterFactory(json: Json): Converter.Factory {
        return json.asConverterFactory(MediaType.get("application/json; charset=UTF8"))
    }

    @Singleton
    @Provides
    fun providesJsonInstance(): Json {
        return Json { ignoreUnknownKeys = true }
    }

    @Singleton
    @Provides
    fun providesOkHttpClient(application: Application): OkHttpClient {
        val builder = OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val input = application.resources.openRawResource(R.raw.isrgrootx1)
                    val certificate = CertificateFactory.getInstance("X.509").generateCertificates(input).single() as X509Certificate
                    val certificates = HandshakeCertificates.Builder()
                        .addTrustedCertificate(certificate)
                        .addPlatformTrustedCertificates()
                        .build()
                    val trustManager = certificates.trustManager()
                    val sslContext = SSLContext.getInstance(TlsVersion.TLS_1_2.javaName())
                    sslContext.init(null, arrayOf(trustManager), null)
                    sslSocketFactory(TlsSocketFactory(sslContext.socketFactory), trustManager)
                    val cipherSuites = ConnectionSpec.MODERN_TLS.cipherSuites()!!.toMutableList().apply {
                        add(CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA)
                    }.toTypedArray()
                    val cs = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                        .cipherSuites(*cipherSuites)
                        .build()
                    connectionSpecs(arrayListOf(cs))
                } catch (e: Exception) {
                    Log.e("OkHttpTLSCompat", "Error while setting TLS 1.2 compatibility", e)
                }
            } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                try {
                    val input = application.resources.openRawResource(R.raw.isrgrootx1)
                    val certificate = CertificateFactory.getInstance("X.509").generateCertificates(input).single() as X509Certificate
                    val certificates = HandshakeCertificates.Builder()
                        .addTrustedCertificate(certificate)
                        .addPlatformTrustedCertificates()
                        .build()
                    sslSocketFactory(certificates.sslSocketFactory(), certificates.trustManager())
                } catch (e: Exception) {

                }
            }
            connectTimeout(5, TimeUnit.MINUTES)
            writeTimeout(5, TimeUnit.MINUTES)
            readTimeout(5, TimeUnit.MINUTES)
        }
        return builder.build()
    }
}
