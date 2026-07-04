package com.streamvault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.streamvault.player.core.PlayerConfig
import com.streamvault.player.core.PlayerEngine
import com.streamvault.player.core.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var engine: PlayerEngine? = null
    private var mediaSession: MediaSession? = null

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        engine = PlayerEngine(PlayerConfig())
        mediaSession = MediaSession(this, "StreamVault").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { engine?.play() }
                override fun onPause() { engine?.pause() }
                override fun onSeekTo(pos: Long) { engine?.seekTo(pos) }
            })
            isActive = true
        }
    }

    fun play(url: String, title: String, channelName: String) {
        val player = engine ?: return
        player.loadStreams(url, url, url)

        serviceScope.launch {
            player.state.collect { state ->
                if (state is PlayerState.Playing || state is PlayerState.Buffering) {
                    val pos = player.position.first()
                    mediaSession?.setPlaybackState(
                        PlaybackState.Builder()
                            .setState(PlaybackState.STATE_PLAYING, pos, 1f)
                            .setActions(
                                PlaybackState.ACTION_PLAY or
                                PlaybackState.ACTION_PAUSE or
                                PlaybackState.ACTION_SEEK_TO
                            )
                            .build()
                    )
                }
            }
        }

        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, channelName)
                .build()
        )

        startForeground(NOTIFICATION_ID, buildNotification(title, channelName))
    }

    fun stop() {
        engine?.stop()
        engine?.release()
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
        engine?.release()
        engine = null
        mediaSession?.release()
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "streamvault_playback"
        const val NOTIFICATION_ID = 1
    }
}
