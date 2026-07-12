package com.streamvault.app.di

import com.streamvault.app.data.bootstrap.VisitorDataBootstrapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BootstrapModule {

    @Provides
    @Singleton
    fun provideVisitorDataBootstrapper(
        @ApplicationContext context: android.content.Context,
        @Named("general") okHttpClient: OkHttpClient
    ): VisitorDataBootstrapper {
        return VisitorDataBootstrapper(context, okHttpClient)
    }
}