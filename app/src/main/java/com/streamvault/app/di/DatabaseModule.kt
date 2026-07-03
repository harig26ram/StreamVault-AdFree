package com.streamvault.app.di

import android.content.Context
import androidx.room.Room
import com.streamvault.app.data.local.AppDatabase
import com.streamvault.app.data.local.VideoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "streamvault.db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }
}