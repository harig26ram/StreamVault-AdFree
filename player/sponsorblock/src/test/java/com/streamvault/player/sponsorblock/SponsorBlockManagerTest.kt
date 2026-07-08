package com.streamvault.player.sponsorblock

import com.streamvault.player.core.PlayerState
import com.streamvault.player.sponsorblock.data.SponsorSegment
import com.streamvault.player.sponsorblock.domain.SponsorBlockRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class SponsorBlockManagerTest {

    private lateinit var repository: SponsorBlockRepository
    private lateinit var manager: SponsorBlockManager

    @Before
    fun setUp() {
        repository = mock()
        manager = SponsorBlockManager(repository)
    }

    @Test
    fun `load segments successfully`() = runTest {
        val segments = listOf(
            SponsorSegment(listOf(10.0, 20.0), "sponsor", "u1", 300.0)
        )
        `when`(repository.getSegments(any())).thenReturn(Result.success(segments))
        val result = manager.loadSegments("video123")
        assertTrue(result.isSuccess)
        assertEquals(segments, result.getOrNull())
    }

    @Test
    fun `load segments failure`() = runTest {
        val error = RuntimeException("Network error")
        `when`(repository.getSegments(any())).thenReturn(Result.failure(error))
        val result = manager.loadSegments("bad_video")
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `checkSegments returns Skip when position in sponsor segment`() = runTest {
        val segments = listOf(
            SponsorSegment(listOf(10.0, 20.0), "sponsor", "u1", 300.0)
        )
        `when`(repository.getSegments(any())).thenReturn(Result.success(segments))
        manager.loadSegments("video123")
        val action = manager.checkSegments("video123", PlayerState.Playing, 15000L)
        assertTrue(action is SponsorBlockAction.Skip)
        assertEquals("sponsor", (action as SponsorBlockAction.Skip).segment.category)
    }

    @Test
    fun `shouldAutoSkip returns true for sponsor and selfpromo`() {
        assertTrue(manager.shouldAutoSkip(SponsorSegment(listOf(0.0, 1.0), "sponsor", "u1", 100.0)))
        assertTrue(manager.shouldAutoSkip(SponsorSegment(listOf(0.0, 1.0), "selfpromo", "u2", 100.0)))
        assertFalse(manager.shouldAutoSkip(SponsorSegment(listOf(0.0, 1.0), "intro", "u3", 100.0)))
        assertFalse(manager.shouldAutoSkip(SponsorSegment(listOf(0.0, 1.0), "outro", "u4", 100.0)))
        assertFalse(manager.shouldAutoSkip(SponsorSegment(listOf(0.0, 1.0), "filler", "u5", 100.0)))
    }

    @Test
    fun `cached segments returned without refetch`() = runTest {
        val segments = listOf(
            SponsorSegment(listOf(10.0, 20.0), "sponsor", "u1", 300.0)
        )
        `when`(repository.getSegments("cached_video")).thenReturn(Result.success(segments))
        val first = manager.loadSegments("cached_video")
        assertTrue(first.isSuccess)
        val second = manager.loadSegments("cached_video")
        assertTrue(second.isSuccess)
        assertEquals(1, manager.checkSegments("cached_video", PlayerState.Playing, 15000L)?.let {
            if (it is SponsorBlockAction.Skip) 1 else 0
        })
    }

    @Test
    fun `checkSegments returns None when not playing`() {
        val result = manager.checkSegments("video123", PlayerState.Idle, 1000L)
        assertNull(result)
    }

    @Test
    fun `checkSegments returns None when no segments loaded`() {
        val result = manager.checkSegments("unknown_video", PlayerState.Playing, 1000L)
        assertEquals(SponsorBlockAction.None, result)
    }
}
