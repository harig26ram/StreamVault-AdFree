package com.streamvault.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PlayerEngineTest {

    @Test
    fun `engine starts in idle state`() {
        val engine = PlayerEngine()
        assertEquals(PlayerState.Idle, engine.state.value)
    }

    @Test
    fun `setPlaybackSpeed clamps to valid range`() {
        val engine = PlayerEngine()
        engine.setPlaybackSpeed(5.0f)
        engine.setPlaybackSpeed(0.1f)
        assertEquals(0.25f, engine.config.playbackSpeed, 0.01f)
    }

    @Test
    fun `state flows are initialized to defaults`() {
        val engine = PlayerEngine()
        assertEquals(0L, engine.position.value)
        assertEquals(0L, engine.duration.value)
        assertEquals(0, engine.bufferedPercent.value)
    }

    @Test
    fun `release transitions to idle`() {
        val engine = PlayerEngine()
        engine.release()
        assertEquals(PlayerState.Idle, engine.state.value)
    }

    @Test
    fun `stop after load returns to idle`() {
        val engine = PlayerEngine()
        assertEquals(PlayerState.Idle, engine.state.value)
        engine.stop()
        assertEquals(PlayerState.Idle, engine.state.value)
    }

    @Test
    fun `setSurface does not throw`() {
        val engine = PlayerEngine()
        engine.setSurface(null)
    }

    @Test
    fun `multiple pause calls are safe`() {
        val engine = PlayerEngine()
        engine.pause()
        engine.pause()
        engine.pause()
    }

    @Test
    fun `play in idle does nothing`() {
        val engine = PlayerEngine()
        engine.play()
        assertEquals(PlayerState.Idle, engine.state.value)
    }

    @Test
    fun `can set playback speed`() {
        val engine = PlayerEngine()
        engine.setPlaybackSpeed(2.0f)
        assertEquals(2.0f, engine.config.playbackSpeed, 0.01f)
    }

    @Test
    fun `state transitions through expected lifecycle`() {
        val engine = PlayerEngine()
        assertEquals(PlayerState.Idle, engine.state.value)
        engine.release()
        assertEquals(PlayerState.Idle, engine.state.value)
    }

    @Test
    fun `config defaults are sensible`() {
        val config = PlayerConfig()
        assertEquals(1920, config.preferredVideoWidth)
        assertEquals(1080, config.preferredVideoHeight)
        assertEquals(1.0f, config.playbackSpeed, 0.01f)
        assertTrue(config.bufferSizeBytes > 0)
        assertTrue(config.connectTimeoutMs > 0)
    }
}
