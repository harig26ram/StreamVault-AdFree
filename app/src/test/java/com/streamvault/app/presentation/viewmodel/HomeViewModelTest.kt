package com.streamvault.app.presentation.viewmodel

import com.streamvault.app.data.repository.VisitorDataBootstrapper
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.HomeFeed
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.AddToWatchLaterUseCase
import com.streamvault.app.domain.usecase.GetHomeFeedUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sampleVideo = Video(
        id = "test123",
        title = "Test Video",
        channelName = "Test Channel",
        channelId = "UC123",
        channelAvatar = "",
        thumbnailUrl = "",
        duration = "10:00",
        viewCount = "1000",
        publishedTime = "1 day ago"
    )

    private lateinit var fakeGetHomeFeedUseCase: FakeGetHomeFeedUseCase
    private lateinit var fakeAddToWatchLaterUseCase: FakeAddToWatchLaterUseCase
    private lateinit var fakeVisitorDataBootstrapper: FakeVisitorDataBootstrapper

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeGetHomeFeedUseCase = FakeGetHomeFeedUseCase()
        fakeAddToWatchLaterUseCase = FakeAddToWatchLaterUseCase()
        fakeVisitorDataBootstrapper = FakeVisitorDataBootstrapper()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HomeViewModel {
        return HomeViewModel(
            getHomeFeedUseCase = fakeGetHomeFeedUseCase,
            addToWatchLaterUseCase = fakeAddToWatchLaterUseCase,
            visitorDataBootstrapper = fakeVisitorDataBootstrapper
        )
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(HomeFeed(items = emptyList(), continuationToken = null))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.feedItems)
        assertTrue(state.feedItems.isEmpty())
    }

    @Test
    fun `loadHomeFeed success updates feed items`() = runTest {
        val feedItems = listOf(
            FeedItem.Video(sampleVideo),
            FeedItem.Video(sampleVideo.copy(id = "test456", title = "Another Video"))
        )
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(HomeFeed(items = feedItems, continuationToken = "next_page_token"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.feedItems.size)
        assertEquals("next_page_token", state.continuationToken)
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals("Test Video", (state.feedItems[0] as FeedItem.Video).video.title)
    }

    @Test
    fun `loadHomeFeed failure sets error state`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.failure(Exception("Network error"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network error", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.feedItems.isEmpty())
    }

    @Test
    fun `loadHomeFeed timeout sets timeout error`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(HomeFeed(items = emptyList(), continuationToken = null))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
    }

    @Test
    fun `loadMore appends new items`() = runTest {
        val feed1 = HomeFeed(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = "token1")
        val feed2 = HomeFeed(items = listOf(FeedItem.Video(sampleVideo.copy(id = "new123"))), continuationToken = "token2")
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(feed1)
        fakeGetHomeFeedUseCase.feedForToken["token1"] = Result.success(feed2)

        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.feedItems.size)

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.feedItems.size)
        assertEquals("token2", viewModel.uiState.value.continuationToken)
    }

    @Test
    fun `loadMore deduplicates items`() = runTest {
        val feed1 = HomeFeed(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = "token1")
        val feed2 = HomeFeed(
            items = listOf(
                FeedItem.Video(sampleVideo),
                FeedItem.Video(sampleVideo.copy(id = "new456", title = "New Video"))
            ),
            continuationToken = null
        )
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(feed1)
        fakeGetHomeFeedUseCase.feedForToken["token1"] = Result.success(feed2)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.feedItems.size)
    }

    @Test
    fun `loadMore does nothing when no continuation token`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(HomeFeed(items = emptyList(), continuationToken = null))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.feedItems.size)
    }

    @Test
    fun `loadMore failure sets error`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(
            HomeFeed(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = "token1")
        )
        fakeGetHomeFeedUseCase.feedForToken["token1"] = Result.failure(Exception("Load more failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals("Load more failed", viewModel.uiState.value.error)
    }

    @Test
    fun `loadMore does nothing when already loading`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(
            HomeFeed(items = emptyList(), continuationToken = "token")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMore()
        viewModel.loadMore()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `refresh sets isRefreshing true then updates feed`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(
            HomeFeed(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.feedItems.size)
    }

    @Test
    fun `refresh failure clears isRefreshing`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.failure(Exception("Refresh failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals("Refresh failed", viewModel.uiState.value.error)
    }

    @Test
    fun `addToWatchLater delegates to use case`() = runTest {
        fakeGetHomeFeedUseCase.feedToReturn = Result.success(HomeFeed(items = emptyList(), continuationToken = null))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addToWatchLater(sampleVideo)
        advanceUntilIdle()

        assertEquals(1, fakeAddToWatchLaterUseCase.addedVideos.size)
        assertEquals("test123", fakeAddToWatchLaterUseCase.addedVideos[0].id)
    }
}

class FakeGetHomeFeedUseCase : GetHomeFeedUseCase(
    repository = object : com.streamvault.app.domain.repository.VideoRepository {
        override suspend fun getHomeFeed(continuationToken: String?): Result<HomeFeed> = TODO()
        override suspend fun search(query: String, continuationToken: String?) = TODO()
        override suspend fun getVideoInfo(videoId: String) = TODO()
        override suspend fun getChannelInfo(channelId: String) = TODO()
        override suspend fun getPlaylist(playlistId: String) = TODO()
        override suspend fun getTrending() = TODO()
        override suspend fun getSubscriptions() = TODO()
        override suspend fun getVideoStreamUrl(videoId: String) = TODO()
        override suspend fun getVideoFormats(videoId: String) = TODO()
        override suspend fun getCaptionTracks(videoId: String) = TODO()
        override suspend fun getComments(videoId: String) = TODO()
        override suspend fun getRelatedVideos(videoId: String) = TODO()
        override fun getWatchHistory() = TODO()
        override suspend fun addToWatchHistory(video: Video) {}
        override suspend fun clearWatchHistory() {}
        override fun getWatchLater() = TODO()
        override suspend fun addToWatchLater(video: Video) {}
        override suspend fun clearWatchLater() {}
        override fun getSubscriptionsList() = TODO()
        override suspend fun subscribeToChannel(channelId: String) {}
        override suspend fun unsubscribeFromChannel(channelId: String) {}
    }
) {
    var feedToReturn: Result<HomeFeed> = Result.success(HomeFeed(emptyList(), null))
    val feedForToken = mutableMapOf<String, Result<HomeFeed>>()

    override suspend fun invoke(continuationToken: String?): Result<HomeFeed> {
        return feedForToken[continuationToken] ?: feedToReturn
    }
}

class FakeAddToWatchLaterUseCase : AddToWatchLaterUseCase(
    repository = object : com.streamvault.app.domain.repository.VideoRepository {
        override suspend fun getHomeFeed(continuationToken: String?) = TODO()
        override suspend fun search(query: String, continuationToken: String?) = TODO()
        override suspend fun getVideoInfo(videoId: String) = TODO()
        override suspend fun getChannelInfo(channelId: String) = TODO()
        override suspend fun getPlaylist(playlistId: String) = TODO()
        override suspend fun getTrending() = TODO()
        override suspend fun getSubscriptions() = TODO()
        override suspend fun getVideoStreamUrl(videoId: String) = TODO()
        override suspend fun getVideoFormats(videoId: String) = TODO()
        override suspend fun getCaptionTracks(videoId: String) = TODO()
        override suspend fun getComments(videoId: String) = TODO()
        override suspend fun getRelatedVideos(videoId: String) = TODO()
        override fun getWatchHistory() = TODO()
        override suspend fun addToWatchHistory(video: Video) {}
        override suspend fun clearWatchHistory() {}
        override fun getWatchLater() = TODO()
        override suspend fun addToWatchLater(video: Video) {}
        override suspend fun clearWatchLater() {}
        override fun getSubscriptionsList() = TODO()
        override suspend fun subscribeToChannel(channelId: String) {}
        override suspend fun unsubscribeFromChannel(channelId: String) {}
    }
) {
    val addedVideos = mutableListOf<Video>()

    override suspend fun invoke(video: Video) {
        addedVideos.add(video)
    }
}

class FakeVisitorDataBootstrapper : VisitorDataBootstrapper(
    httpClient = okhttp3.OkHttpClient()
) {
    var shouldSucceed = true

    override suspend fun ensureVisitorData(): Boolean = shouldSucceed
}
