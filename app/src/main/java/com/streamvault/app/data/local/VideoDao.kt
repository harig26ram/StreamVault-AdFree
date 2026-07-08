package com.streamvault.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {

    @Query("SELECT * FROM watch_history ORDER BY watched_at DESC LIMIT 100")
    fun getWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history ORDER BY watched_at DESC LIMIT 20")
    suspend fun getWatchHistorySync(): List<WatchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchHistory(entity: WatchHistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    @Query("SELECT * FROM subscriptions ORDER BY subscribed_at DESC")
    fun getSubscriptions(): Flow<List<SubscriptionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(entity: SubscriptionEntity)

    @Delete
    suspend fun deleteSubscription(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE channel_id = :channelId")
    suspend fun deleteSubscription(channelId: String)

    @Query("SELECT * FROM watch_later ORDER BY added_at DESC")
    fun getWatchLater(): Flow<List<WatchLaterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWatchLater(entity: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE video_id = :videoId")
    suspend fun removeFromWatchLater(videoId: String)

    @Query("DELETE FROM watch_later")
    suspend fun clearWatchLater()

    @Query("SELECT last_position_ms FROM watch_history WHERE video_id = :videoId")
    suspend fun getSavedPosition(videoId: String): Long?

    @Query("UPDATE watch_history SET last_position_ms = :positionMs WHERE video_id = :videoId")
    suspend fun updateLastPosition(videoId: String, positionMs: Long)

    // Download queries
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(entity: DownloadEntity)

    @Query("UPDATE downloads SET download_status = :status, progress = :progress WHERE video_id = :videoId")
    suspend fun updateDownloadStatus(videoId: String, status: String, progress: Int)

    @Query("UPDATE downloads SET file_path = :filePath, file_size = :fileSize, download_status = :status, progress = 100 WHERE video_id = :videoId")
    suspend fun updateDownloadComplete(videoId: String, filePath: String, fileSize: Long, status: String)

    @Query("SELECT * FROM downloads WHERE video_id = :videoId")
    suspend fun getDownload(videoId: String): DownloadEntity?

    @Query("SELECT * FROM downloads ORDER BY downloaded_at DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE video_id = :videoId")
    fun observeDownload(videoId: String): Flow<DownloadEntity?>

    @Query("DELETE FROM downloads WHERE video_id = :videoId")
    suspend fun deleteDownload(videoId: String)

    @Query("SELECT * FROM downloads WHERE download_status IN ('PENDING', 'DOWNLOADING', 'PAUSED')")
    suspend fun getPendingDownloads(): List<DownloadEntity>
}