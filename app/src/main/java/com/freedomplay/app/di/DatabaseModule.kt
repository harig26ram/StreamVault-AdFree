package com.freedomplay.app.di

import android.content.Context
import androidx.room.Room
import com.freedomplay.app.data.local.db.DownloadDao
import com.freedomplay.app.data.local.db.FreedomPlayDatabase
import com.freedomplay.app.data.local.db.PlaylistDao
import com.freedomplay.app.data.local.db.WatchHistoryDao
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
    fun provideDatabase(@ApplicationContext context: Context): FreedomPlayDatabase {
        return Room.databaseBuilder(
            context,
            FreedomPlayDatabase::class.java,
            "freedomplay_database"
        ).fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: FreedomPlayDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: FreedomPlayDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideWatchHistoryDao(database: FreedomPlayDatabase): WatchHistoryDao {
        return database.watchHistoryDao()
    }
}
