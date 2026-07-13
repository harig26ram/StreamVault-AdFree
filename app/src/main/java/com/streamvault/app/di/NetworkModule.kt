package com.streamvault.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.data.api.YouTubeApiService
import com.streamvault.app.data.bootstrap.VisitorDataBootstrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()

    private const val INNERTUBE_API_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"

    private val refreshLock = Any()

    @Provides
    @Singleton
    @Named("youtube")
    fun provideYouTubeOkHttpClient(
        authManager: AuthManager,
        bootstrapper: VisitorDataBootstrapper
    ): OkHttpClient {
        val clientBuilder = OkHttpClient.Builder()

        if (com.streamvault.app.BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            clientBuilder.addInterceptor(logging)
        }

        clientBuilder.addInterceptor { chain ->
            val original = chain.request()
            val url = original.url

            val newUrlBuilder = if (url.encodedPath.startsWith("/youtubei/v1/")) {
                // Get API key from bootstrapper (cached or fresh)
                val effectiveKey = bootstrapper.getCachedApiKey() ?: INNERTUBE_API_KEY
                url.newBuilder()
                    .addQueryParameter("key", effectiveKey)
            } else {
                url.newBuilder()
            }

            val newUrl = newUrlBuilder.build()

            val builder = original.newBuilder()
                .url(newUrl)
                .header("User-Agent", "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Content-Type", "application/json")

            val accessToken = authManager.getAccessToken()
            if (!accessToken.isNullOrEmpty()) {
                builder.header("Authorization", "Bearer $accessToken")
            }

            // Get visitorData from bootstrapper (cached or fresh)
            val visitorData = bootstrapper.getCachedVisitorData()
            visitorData?.let {
                builder.header("X-Goog-Visitor-Data", it)
            }

            chain.proceed(builder.build())
        }

        clientBuilder.addInterceptor { chain ->
            val request = chain.proceed(chain.request())

            if (request.code == 401) {
                request.close()
                val newToken = synchronized(refreshLock) {
                    runBlocking { authManager.refreshAccessToken() }
                }
                if (newToken != null) {
                    val newRequest = chain.request().newBuilder()
                        .header("Authorization", "Bearer $newToken")
                        .build()
                    return@addInterceptor chain.proceed(newRequest)
                }
                chain.proceed(chain.request())
            } else {
                request
            }
        }

        return clientBuilder
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("general")
    fun provideGeneralOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("youtube")
    fun provideRetrofit(@Named("youtube") okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://www.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideYouTubeApiService(@Named("youtube") retrofit: Retrofit): YouTubeApiService {
        return retrofit.create(YouTubeApiService::class.java)
    }
}
