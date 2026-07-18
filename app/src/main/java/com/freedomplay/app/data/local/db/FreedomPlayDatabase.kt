package com.freedomplay.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DownloadEntity::class, PlaylistEntity::class, PlaylistVideoEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FreedomPlayDatabase : RoomDatabase() {

    abstract fun downloadDao(): DownloadDao
    abstract fun playlistDao(): PlaylistDao
}
