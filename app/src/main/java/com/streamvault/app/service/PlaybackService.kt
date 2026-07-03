package com.streamvault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem

class PlaybackService : Service() {

    private val binder = LocalBinder()
    var exoPlayer: ExoPlayer? = null
        private set
    private var mediaSession: MediaSessionCompat? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        exoPlayer = ExoPlayer.Builder(this).build()
        mediaSession = MediaSessionCompat(this, "StreamVault").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { exoPlayer?.play() }
                override fun onPause() { exoPlayer?.pause() }
                override fun onSeekTo(pos: Long) { exoPlayer?.seekTo(pos) }
            })
        }
    }

    fun play(url: String, title: String, channelName: String) {
        val player = exoPlayer ?: return
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        mediaSession?.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, channelName)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, channelName)
                .build()
        )

        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, player.currentPosition, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )

        startForeground(NOTIFICATION_ID, buildNotification(title, channelName))
    }

    fun stop() {
        exoPlayer?.stop()
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "StreamVault playback controls"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, channelName: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(channelName)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        mediaSession?.release()
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "streamvault_playback"
        const val NOTIFICATION_ID = 1
    }
}
