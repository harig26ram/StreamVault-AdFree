package com.streamvault.player.core

import kotlin.math.roundToLong

class PlaybackClock {
    @Volatile private var running = false
    @Volatile private var paused = false
    @Volatile private var speed = 1.0f
    private var baseMediaTimeUs = 0L
    private var baseSystemTimeNs = 0L
    private var pauseSystemTimeNs = 0L

    val mediaTimeUs: Long
        get() {
            if (!running) return 0L
            if (paused) return baseMediaTimeUs
            val elapsedNs = System.nanoTime() - baseSystemTimeNs
            return baseMediaTimeUs + (elapsedNs * speed.toDouble()).roundToLong() / 1000L
        }

    val mediaTimeMs: Long get() = mediaTimeUs / 1000L

    fun start(initialSpeed: Float = 1.0f) {
        speed = initialSpeed.coerceIn(0.25f, 4.0f)
        baseMediaTimeUs = 0L
        baseSystemTimeNs = System.nanoTime()
        running = true
        paused = false
    }

    fun startAt(timeUs: Long, initialSpeed: Float = 1.0f) {
        speed = initialSpeed.coerceIn(0.25f, 4.0f)
        baseMediaTimeUs = timeUs
        baseSystemTimeNs = System.nanoTime()
        running = true
        paused = false
    }

    fun pause() {
        if (!running || paused) return
        val elapsedNs = System.nanoTime() - baseSystemTimeNs
        baseMediaTimeUs += (elapsedNs * speed.toDouble()).roundToLong() / 1000L
        paused = true
        pauseSystemTimeNs = System.nanoTime()
    }

    fun resume() {
        if (!running || !paused) return
        val pauseDurationNs = System.nanoTime() - pauseSystemTimeNs
        baseSystemTimeNs += pauseDurationNs
        paused = false
    }

    fun reset() {
        running = false
        paused = false
        baseMediaTimeUs = 0L
        baseSystemTimeNs = 0L
        pauseSystemTimeNs = 0L
        speed = 1.0f
    }

    fun setSpeed(newSpeed: Float) {
        val current = mediaTimeUs
        speed = newSpeed.coerceIn(0.25f, 4.0f)
        if (running && !paused) {
            baseMediaTimeUs = current
            baseSystemTimeNs = System.nanoTime()
        }
    }

    fun targetWakeupTimeNsForPts(ptsUs: Long): Long {
        if (!running) return System.nanoTime()
        val playedUs = mediaTimeUs
        val deltaUs = ptsUs - playedUs
        val adjustedDeltaNs = (deltaUs * 1000L / speed.toDouble()).roundToLong()
        return System.nanoTime() + adjustedDeltaNs
    }
}
