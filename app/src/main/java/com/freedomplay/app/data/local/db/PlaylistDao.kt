package com.freedomplay.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val videoCount: Int
)

data class PlaylistWithVideos(
    val playlist: PlaylistEntity,
    val videos: List<PlaylistVideoEntity>
)

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: Long)

    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?

    @Query("SELECT p.*, COUNT(pv.id) AS videoCount FROM playlists p LEFT JOIN playlist_videos pv ON p.id = pv.playlistId GROUP BY p.id ORDER BY p.updatedAt DESC")
    fun getPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getVideosInPlaylist(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getVideosInPlaylistOnce(playlistId: Long): List<PlaylistVideoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideo(video: PlaylistVideoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addVideos(videos: List<PlaylistVideoEntity>)

    @Delete
    suspend fun removeVideo(video: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun removeVideoByVideoId(playlistId: Long, videoId: String)

    @Query("DELETE FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlist_videos SET position = :position WHERE id = :videoId")
    suspend fun updateVideoPosition(videoId: Long, position: Int)

    @Query("UPDATE playlist_videos SET position = :position WHERE playlistId = :playlistId AND videoId = :videoId")
    suspend fun updateVideoPositionByVideoId(playlistId: Long, videoId: String, position: Int)

    @Query("SELECT COALESCE(MAX(position), 0) + 1 FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getNextPosition(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM playlist_videos WHERE playlistId = :playlistId")
    suspend fun getVideoCount(playlistId: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_videos WHERE playlistId = :playlistId AND videoId = :videoId)")
    suspend fun isVideoInPlaylist(playlistId: Long, videoId: String): Boolean

    @Transaction
    suspend fun reorderVideos(playlistId: Long, orderedVideoIds: List<String>) {
        orderedVideoIds.forEachIndexed { index, videoId ->
            updateVideoPositionByVideoId(playlistId, videoId, index)
        }
    }
}
