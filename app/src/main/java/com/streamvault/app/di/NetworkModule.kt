package com.streamvault.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.streamvault.app.auth.AuthManager
import com.streamvault.app.data.api.SponsorBlockApi
import com.streamvault.app.data.api.YouTubeApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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

    @Provides
    @Singleton
    fun provideOkHttpClient(authManager: AuthManager): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .addHeader("User-Agent", "com.google.android.youtube/21.03.36(Linux; U; Android 16; en_US; SM-S908E Build/TP1A.220624.014) gzip")
                    .addHeader("Accept-Language", "en-US,en;q=0.9")

                val token = authManager.getAccessToken()
                if (!token.isNullOrBlank()) {
                    builder.addHeader("Authorization", "Bearer $token")
                }

                chain.proceed(builder.build())
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    @Provides
    @Singleton
    @Named("youtube")
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
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

    @Provides
    @Singleton
    @Named("sponsorblock")
    fun provideSponsorBlockRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://sponsor.ajay.app/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideSponsorBlockApi(@Named("sponsorblock") retrofit: Retrofit): SponsorBlockApi {
        return retrofit.create(SponsorBlockApi::class.java)
    }
}
