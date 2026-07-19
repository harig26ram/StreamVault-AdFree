package com.freedomplay.app.di

import android.content.Context
import com.freedomplay.app.data.api.invidious.InvidiousApiService
import com.freedomplay.app.data.api.piped.PipedApiService
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PipedRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InvidiousRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    val PIPED_FALLBACK_URLS = listOf(
        "https://pipedapi.r4fo.com/",           // Sometimes works for streams
        "https://pipedapi.in.projectsegfau.lt/", // Sometimes works
        "https://api.piped.private.coffee/",     // Occasionally works
        "https://pipedapi.kavin.rocks/",         // Large instance
        "https://pipedapi.leptons.xyz/"          // Backup
    )

    val INVIDIOUS_FALLBACK_URLS = listOf(
        "https://inv.zoomerville.com/",          // api:true, cors:true — best for video streams
        "https://invidious.materialio.us/",      // Trending works reliably
        "https://inv.nadeko.net/",               // 99.8% uptime, 4.3k users
        "https://invidious.nerdvpn.de/",         // 99.5% uptime
        "https://iv.ggtyler.dev/",               // Large instance
        "https://yewtu.be/",                     // Well-known
        "https://invidious.f5.si/",              // 99.4% uptime
        "https://yt.chocolatemoo53.com/"         // 94.8% uptime, US-based
    )

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val cache = Cache(File(context.cacheDir, "http_cache"), 50L * 1024 * 1024)
        val builder = OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val all = InetAddress.getAllByName(hostname).toList()
                    val ipv4 = all.filterIsInstance<Inet4Address>()
                    return if (ipv4.isNotEmpty()) ipv4 else all
                }
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

        if (com.freedomplay.app.BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    @PipedRetrofit
    fun providePipedRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(PIPED_FALLBACK_URLS.first())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    @InvidiousRetrofit
    fun provideInvidiousRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(INVIDIOUS_FALLBACK_URLS.first())
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun providePipedApiService(@PipedRetrofit retrofit: Retrofit): PipedApiService {
        return retrofit.create(PipedApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideInvidiousApiService(@InvidiousRetrofit retrofit: Retrofit): InvidiousApiService {
        return retrofit.create(InvidiousApiService::class.java)
    }
}
