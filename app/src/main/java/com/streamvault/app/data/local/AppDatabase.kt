package com.streamvault.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        WatchHistoryEntity::class,
        SubscriptionEntity::class,
        WatchLaterEntity::class,
        DownloadEntity::class,
        SettingEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
}