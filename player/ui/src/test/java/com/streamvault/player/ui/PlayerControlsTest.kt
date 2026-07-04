package com.streamvault.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.streamvault.player.core.PlayerState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerControlsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playButtonShowsWhenPaused() {
        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Paused,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Play").assertIsDisplayed()
    }

    @Test
    fun pauseButtonShowsWhenPlaying() {
        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Playing,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun playPauseClickTriggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Paused,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = { clicked = true },
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Play").performClick()
        assert(clicked)
    }

    @Test
    fun controlButtonsAreDisplayed() {
        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Playing,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Captions").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Quality").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Subscriptions").assertIsDisplayed()
    }

    @Test
    fun speedLabelIsDisplayed() {
        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Playing,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.5f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithText("1.5x").assertIsDisplayed()
    }

    @Test
    fun qualityClickTriggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Playing,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = { clicked = true },
                onCaptionClick = {},
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Quality").performClick()
        assert(clicked)
    }

    @Test
    fun captionClickTriggersCallback() {
        var clicked = false

        composeTestRule.setContent {
            PlayerControlsOverlay(
                state = PlayerState.Playing,
                currentPositionMs = 0L,
                durationMs = 100000L,
                bufferedPercent = 50,
                playbackSpeed = 1.0f,
                isSubscribedToSponsorBlock = false,
                onPlayPauseClick = {},
                onSeek = {},
                onSpeedChange = {},
                onQualityClick = {},
                onCaptionClick = { clicked = true },
                onSubscriptionsClick = {}
            )
        }

        composeTestRule.onNodeWithContentDescription("Captions").performClick()
        assert(clicked)
    }
}
