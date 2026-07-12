package com.streamvault.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN view_count TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN published_time TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN video_url TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS watch_later (video_id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL DEFAULT '', channel_name TEXT NOT NULL DEFAULT '', thumbnail_url TEXT NOT NULL DEFAULT '', added_at INTEGER NOT NULL DEFAULT 0)")
            } catch (_: Exception) {
            }
            try {
                db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL DEFAULT '')")
            } catch (_: Exception) {
            }
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE watch_history ADD COLUMN last_position_ms INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS downloads (
                        video_id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL DEFAULT '',
                        channel_name TEXT NOT NULL DEFAULT '',
                        thumbnail_url TEXT NOT NULL DEFAULT '',
                        file_path TEXT NOT NULL DEFAULT '',
                        file_size INTEGER NOT NULL DEFAULT 0,
                        download_status TEXT NOT NULL DEFAULT 'PENDING',
                        progress INTEGER NOT NULL DEFAULT 0,
                        downloaded_at INTEGER NOT NULL DEFAULT 0
                    )"""
                )
            } catch (_: Exception) {}
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN audio_url TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN video_url TEXT NOT NULL DEFAULT ''")
            } catch (_: Exception) {}
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_audio_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_video_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_audio_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_video_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Ensure columns exist with correct schema
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_audio_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE downloads ADD COLUMN downloaded_video_bytes INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "streamvault.db"
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideVideoDao(database: AppDatabase): VideoDao {
        return database.videoDao()
    }
}