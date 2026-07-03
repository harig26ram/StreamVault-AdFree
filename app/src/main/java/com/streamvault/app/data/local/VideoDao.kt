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
}