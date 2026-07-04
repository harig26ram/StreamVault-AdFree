package com.streamvault.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.streamvault.app.R
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
    private var currentState: PlayerState = PlayerState.Idle

    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> engine?.play()
                ACTION_PAUSE -> engine?.pause()
                ACTION_STOP -> {
                    engine?.stop()
                    mediaSession?.isActive = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

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
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_STOP)
        }
        registerReceiver(mediaButtonReceiver, filter)
    }

    fun play(url: String, title: String, channelName: String, isProgressive: Boolean = true) {
        val player = engine ?: return
        if (isProgressive) {
            player.loadStreams(null, null, url)
        } else {
            player.loadStreams(url, url, null)
        }

        serviceScope.launch {
            player.state.collect { state ->
                currentState = state
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
                updateNotification(title, channelName)
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

        val isPlaying = currentState is PlayerState.Playing
        val playPauseAction = if (isPlaying) {
            val pauseIntent = PendingIntent.getBroadcast(
                this, 1,
                Intent(ACTION_PAUSE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "Pause",
                pauseIntent
            )
        } else {
            val playIntent = PendingIntent.getBroadcast(
                this, 2,
                Intent(ACTION_PLAY),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "Play",
                playIntent
            )
        }

        val stopIntent = PendingIntent.getBroadcast(
            this, 3,
            Intent(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_delete,
            "Stop",
            stopIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(channelName)
            .setContentIntent(pendingIntent)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String, channelName: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, channelName))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> engine?.play()
            ACTION_PAUSE -> engine?.pause()
            ACTION_STOP -> {
                engine?.stop()
                mediaSession?.isActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        mediaSession?.release()
        mediaSession = null
        engine?.release()
        engine = null
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "streamvault_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.streamvault.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.streamvault.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.streamvault.app.ACTION_STOP"
    }
}
