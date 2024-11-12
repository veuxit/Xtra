package com.github.andreyasadchy.xtra.di

import android.app.Application
import android.os.Build
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloRequest
import com.apollographql.apollo.api.CustomScalarAdapters
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.http.HttpBody
import com.apollographql.apollo.api.http.HttpMethod
import com.apollographql.apollo.api.http.HttpRequest
import com.apollographql.apollo.api.http.HttpRequestComposer
import com.apollographql.apollo.api.json.buildJsonByteString
import com.apollographql.apollo.api.json.writeObject
import com.apollographql.apollo.network.http.HttpNetworkTransport
import com.apollographql.apollo.network.okHttpClient
import com.github.andreyasadchy.xtra.BuildConfig
import com.github.andreyasadchy.xtra.R
import com.github.andreyasadchy.xtra.api.GraphQLApi
import com.github.andreyasadchy.xtra.api.HelixApi
import com.github.andreyasadchy.xtra.api.IdApi
import com.github.andreyasadchy.xtra.api.MiscApi
import com.github.andreyasadchy.xtra.api.UsherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.decodeCertificatePem
import okio.BufferedSink
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

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
        return json.asConverterFactory("application/json; charset=UTF8".toMediaType())
    }

    @Singleton
    @Provides
    fun providesJsonInstance(): Json {
        return Json { ignoreUnknownKeys = true }
    }

    @Singleton
    @Provides
    fun providesApolloClient(okHttpClient: OkHttpClient): ApolloClient {
        val builder = ApolloClient.Builder().apply {
            networkTransport(
                HttpNetworkTransport.Builder()
                    .okHttpClient(okHttpClient)
                    .httpRequestComposer(object : HttpRequestComposer {
                        override fun <D : Operation.Data> compose(apolloRequest: ApolloRequest<D>): HttpRequest {
                            val operationByteString = buildJsonByteString(indent = null) {
                                writeObject {
                                    name("variables")
                                    writeObject {
                                        apolloRequest.operation.serializeVariables(this, apolloRequest.executionContext[CustomScalarAdapters] ?: CustomScalarAdapters.Empty, false)
                                    }
                                    name("query")
                                    value(apolloRequest.operation.document().replaceFirst(apolloRequest.operation.name(), "null"))
                                }
                            }
                            return HttpRequest.Builder(HttpMethod.Post, "https://gql.twitch.tv/gql/").apply {
                                apolloRequest.httpHeaders?.let { addHeaders(it) }
                                body(object : HttpBody {
                                    override val contentType = "application/json"
                                    override val contentLength = operationByteString.size.toLong()

                                    override fun writeTo(bufferedSink: BufferedSink) {
                                        bufferedSink.write(operationByteString)
                                    }
                                })
                            }.build()
                        }
                    })
                    .build()
            )
        }
        return builder.build()
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
}
