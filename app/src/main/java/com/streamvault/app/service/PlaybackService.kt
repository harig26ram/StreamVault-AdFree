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
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.streamvault.app.R
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
    private var audioManager: AudioManager? = null
    private var audioFocusListener: AudioManager.OnAudioFocusChangeListener? = null
    private var hasAudioFocus = false
    private var title: String = ""
    private var channelName: String = ""

    private val mediaButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY -> engine?.play()
                ACTION_PAUSE -> engine?.pause()
                ACTION_STOP -> stopPlayback()
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
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    engine?.pause()
                    abandonAudioFocus()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    engine?.pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    engine?.getEqualizerManager()?.let { /* duck volume via equalizer if needed */ }
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    engine?.play()
                }
            }
        }
        mediaSession = MediaSession(this, "FreedomPlay").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    requestAudioFocus()
                    engine?.play()
                }
                override fun onPause() { engine?.pause() }
                override fun onSeekTo(pos: Long) { engine?.seekTo(pos) }
                override fun onStop() { stopPlayback() }
            })
            isActive = true
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY)
            addAction(ACTION_PAUSE)
            addAction(ACTION_STOP)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            this,
            mediaButtonReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun attachEngine(playerEngine: PlayerEngine) {
        engine = playerEngine
        serviceScope.launch {
            playerEngine.state.collect { state ->
                currentState = state
                updateMediaSessionState()
                if (title.isNotEmpty()) {
                    updateNotification(title, channelName)
                }
                if (state is PlayerState.Ended) {
                    abandonAudioFocus()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else if (state is PlayerState.Error) {
                    Log.w(TAG, "Playback error in background: ${state.message}")
                    if (title.isNotEmpty()) {
                        updateNotification(title, channelName)
                    }
                }
            }
        }
    }

    fun playVideo(
        playerEngine: PlayerEngine,
        videoTitle: String,
        videoChannelName: String,
        thumbnailUrl: String? = null
    ) {
        title = videoTitle
        channelName = videoChannelName
        attachEngine(playerEngine)
        requestAudioFocus()

        mediaSession?.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, videoTitle)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, videoChannelName)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, playerEngine.duration.value)
                .build()
        )

        startForeground(NOTIFICATION_ID, buildNotification(videoTitle, videoChannelName))
    }

    fun stopPlayback() {
        abandonAudioFocus()
        engine?.pause()
        mediaSession?.isActive = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val result = audioManager?.requestAudioFocus(
            audioFocusListener!!,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        audioManager?.abandonAudioFocus(audioFocusListener)
        hasAudioFocus = false
    }

    private fun updateMediaSessionState() {
        val playbackState = when (currentState) {
            is PlayerState.Playing -> PlaybackState.STATE_PLAYING
            is PlayerState.Buffering -> PlaybackState.STATE_BUFFERING
            is PlayerState.Paused -> PlaybackState.STATE_PAUSED
            is PlayerState.Ended -> PlaybackState.STATE_STOPPED
            is PlayerState.Error -> PlaybackState.STATE_ERROR
            else -> PlaybackState.STATE_NONE
        }
        val pos = engine?.position?.value ?: 0L
        val speed = engine?.config?.playbackSpeed ?: 1f
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(playbackState, pos, speed)
                .setActions(
                    PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PAUSE or
                    PlaybackState.ACTION_SEEK_TO or
                    PlaybackState.ACTION_STOP
                )
                .build()
        )
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
            ACTION_STOP -> stopPlayback()
        }
        // Promote to foreground IMMEDIATELY so Android 12+ does not throw
        // ForegroundServiceDidNotStartInTimeException (5s rule) when the engine
        // is attached a moment later via bindService.
        if (engine == null) {
            startForeground(NOTIFICATION_ID, buildLoadingNotification())
        }
        return START_STICKY
    }

    private fun buildLoadingNotification(): Notification {
        return buildNotification("Preparing playback…", "FreedomPlay")
    }

    override fun onDestroy() {
        abandonAudioFocus()
        mediaSession?.release()
        mediaSession = null
        try {
            unregisterReceiver(mediaButtonReceiver)
        } catch (_: Exception) {}
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        const val CHANNEL_ID = "streamvault_playback"
        const val NOTIFICATION_ID = 1
        const val ACTION_PLAY = "com.streamvault.app.ACTION_PLAY"
        const val ACTION_PAUSE = "com.streamvault.app.ACTION_PAUSE"
        const val ACTION_STOP = "com.streamvault.app.ACTION_STOP"
    }
}
