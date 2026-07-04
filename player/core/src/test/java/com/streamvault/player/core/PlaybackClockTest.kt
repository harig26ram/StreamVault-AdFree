package com.streamvault.player.core

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClockTest {

    @Test
    fun `clock starts at zero`() {
        val clock = PlaybackClock()
        assertEquals(0L, clock.mediaTimeMs)
    }

    @Test
    fun `clock advances after start`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        val t1 = clock.mediaTimeUs
        Thread.sleep(100)
        val t2 = clock.mediaTimeUs
        assertTrue("Clock should advance: $t2 > $t1", t2 > t1)
    }

    @Test
    fun `clock pauses and resumes`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        Thread.sleep(50)
        clock.pause()
        val afterPause = clock.mediaTimeUs
        Thread.sleep(50)
        val afterWait = clock.mediaTimeUs
        assertEquals("Time should not change while paused", afterPause, afterWait)
        clock.resume()
        Thread.sleep(50)
        val afterResume = clock.mediaTimeUs
        assertTrue("Clock should advance after resume", afterResume > afterPause)
    }

    @Test
    fun `speed affects clock rate`() {
        val clock = PlaybackClock()
        clock.start(2.0f)
        val t1 = clock.mediaTimeUs
        Thread.sleep(100)
        val t2 = clock.mediaTimeUs
        val elapsed = t2 - t1
        assertTrue("2x speed should advance faster (elapsed=$elapsed)", elapsed > 100)
    }

    @Test
    fun `speed change during playback`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        Thread.sleep(50)
        val t1 = clock.mediaTimeUs
        clock.setSpeed(0.5f)
        Thread.sleep(100)
        val t2 = clock.mediaTimeUs
        val elapsed = t2 - t1
        assertTrue("0.5x speed should advance slower (elapsed=$elapsed)", elapsed < 100_000)
    }

    @Test
    fun `startAt sets initial time`() {
        val clock = PlaybackClock()
        clock.startAt(5_000_000L, 1.0f)
        assertEquals(5000L, clock.mediaTimeMs)
    }

    @Test
    fun `targetWakeupTimeNsForPts returns future for future pts`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        val now = System.nanoTime()
        val futurePts = clock.mediaTimeUs + 1_000_000L
        val wakeup = clock.targetWakeupTimeNsForPts(futurePts)
        assertTrue("Wakeup time should be in the future", wakeup > now)
    }

    @Test
    fun `reset sets everything back to zero`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        Thread.sleep(50)
        assertTrue(clock.mediaTimeUs > 0)
        clock.reset()
        assertEquals(0L, clock.mediaTimeUs)
        assertEquals(0L, clock.mediaTimeMs)
    }

    @Test
    fun `speed change updates media time rate`() {
        val clock = PlaybackClock()
        clock.start(1.0f)
        val t1 = clock.mediaTimeUs
        clock.setSpeed(4.0f)
        Thread.sleep(100)
        val t2 = clock.mediaTimeUs
        val elapsed = t2 - t1
        assertTrue("4x speed should advance faster (elapsed=$elapsed)", elapsed > 200)
    }
}
