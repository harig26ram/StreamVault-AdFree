package com.streamvault.app.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.streamvault.app.data.repository.VisitorDataBootstrapper
import com.streamvault.app.domain.model.FeedItem
import com.streamvault.app.domain.model.SearchResult
import com.streamvault.app.domain.model.Video
import com.streamvault.app.domain.usecase.AddToWatchLaterUseCase
import com.streamvault.app.domain.usecase.SearchUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context
    private lateinit var fakeSearchUseCase: FakeSearchUseCase
    private lateinit var fakeAddToWatchLaterUseCase: FakeSearchAddToWatchLaterUseCase
    private lateinit var fakeVisitorDataBootstrapper: FakeSearchVisitorDataBootstrapper

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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()

        fakeSearchUseCase = FakeSearchUseCase()
        fakeAddToWatchLaterUseCase = FakeSearchAddToWatchLaterUseCase()
        fakeVisitorDataBootstrapper = FakeSearchVisitorDataBootstrapper()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SearchViewModel {
        return SearchViewModel(
            searchUseCase = fakeSearchUseCase,
            addToWatchLaterUseCase = fakeAddToWatchLaterUseCase,
            visitorDataBootstrapper = fakeVisitorDataBootstrapper,
            context = context
        )
    }

    @Test
    fun `initial state has correct defaults`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNull(state.continuationToken)
    }

    @Test
    fun `search with blank query does nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun `search with whitespace only does nothing`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("   ")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun `search success updates results`() = runTest {
        val searchResult = SearchResult(
            items = listOf(FeedItem.Video(sampleVideo)),
            continuationToken = "search_token"
        )
        fakeSearchUseCase.resultToReturn = Result.success(searchResult)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("test query")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.results.size)
        assertEquals("search_token", state.continuationToken)
        assertFalse(state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun `search failure sets error`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.failure(Exception("Search failed"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("bad query")
        advanceUntilIdle()

        assertEquals("Search failed", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun `search adds to history`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("kotlin tutorial")
        advanceUntilIdle()

        val history = viewModel.uiState.value.searchHistory
        assertTrue(history.contains("kotlin tutorial"))
    }

    @Test
    fun `search history deduplicates`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("duplicate query")
        advanceUntilIdle()
        viewModel.search("duplicate query")
        advanceUntilIdle()

        val history = viewModel.uiState.value.searchHistory
        val count = history.count { it == "duplicate query" }
        assertEquals(1, count)
    }

    @Test
    fun `search history limited to 20 items`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        for (i in 1..25) {
            viewModel.search("query $i")
            advanceUntilIdle()
        }

        assertTrue(viewModel.uiState.value.searchHistory.size <= 20)
    }

    @Test
    fun `clearSearch resets state`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = "token")
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("test")
        advanceUntilIdle()

        viewModel.clearSearch()
        val state = viewModel.uiState.value

        assertEquals("", state.query)
        assertTrue(state.results.isEmpty())
        assertNull(state.continuationToken)
        assertNull(state.error)
    }

    @Test
    fun `removeFromHistory removes query`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("remove me")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchHistory.contains("remove me"))

        viewModel.removeFromHistory("remove me")

        assertFalse(viewModel.uiState.value.searchHistory.contains("remove me"))
    }

    @Test
    fun `onQueryChange updates query`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("new query")

        assertEquals("new query", viewModel.uiState.value.query)
    }

    @Test
    fun `onQueryChange with empty clears results`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("something")
        advanceUntilIdle()

        viewModel.onQueryChange("")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.results.isEmpty())
    }

    @Test
    fun `search history loaded from shared prefs on init`() = runTest {
        val existingHistory = """["previous search","another search"]"""
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
            .edit().putString("search_history", existingHistory).commit()

        val viewModel = createViewModel()
        advanceUntilIdle()

        val history = viewModel.uiState.value.searchHistory
        assertEquals(2, history.size)
        assertEquals("previous search", history[0])
        assertEquals("another search", history[1])
    }

    @Test
    fun `empty search history from shared prefs`() = runTest {
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
            .edit().putString("search_history", "[]").commit()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchHistory.isEmpty())
    }

    @Test
    fun `corrupt search history returns empty list`() = runTest {
        context.getSharedPreferences("search_history_prefs", Context.MODE_PRIVATE)
            .edit().putString("search_history", "not json").commit()

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.searchHistory.isEmpty())
    }

    @Test
    fun `loadMore appends results`() = runTest {
        val result1 = SearchResult(
            items = listOf(FeedItem.Video(sampleVideo)),
            continuationToken = "page2"
        )
        val result2 = SearchResult(
            items = listOf(FeedItem.Video(sampleVideo.copy(id = "next999"))),
            continuationToken = null
        )
        fakeSearchUseCase.resultForQuery["query"] = Result.success(result1)
        fakeSearchUseCase.resultForQueryWithToken["query|page2"] = Result.success(result2)
        fakeSearchUseCase.resultToReturn = Result.success(result1)

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("query")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.results.size)

        viewModel.loadMore()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.results.size)
    }

    @Test
    fun `loadMore does nothing when no continuation token`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.search("test")
        advanceUntilIdle()

        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.results.size)
    }

    @Test
    fun `addToWatchLater delegates to use case`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.addToWatchLater(sampleVideo)
        advanceUntilIdle()

        assertEquals(1, fakeAddToWatchLaterUseCase.addedVideos.size)
        assertEquals("test123", fakeAddToWatchLaterUseCase.addedVideos[0].id)
    }

    @Test
    fun `search uses default query from state`() = runTest {
        fakeSearchUseCase.resultToReturn = Result.success(
            SearchResult(items = listOf(FeedItem.Video(sampleVideo)), continuationToken = null)
        )

        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onQueryChange("state query")
        advanceUntilIdle()

        viewModel.search()
        advanceUntilIdle()

        assertEquals("state query", viewModel.uiState.value.query)
        assertEquals(1, viewModel.uiState.value.results.size)
    }
}

class FakeSearchUseCase : SearchUseCase(
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
    var resultToReturn: Result<SearchResult> = Result.success(SearchResult(emptyList(), null))
    val resultForQuery = mutableMapOf<String, Result<SearchResult>>()
    val resultForQueryWithToken = mutableMapOf<String, Result<SearchResult>>()

    override suspend fun invoke(query: String, continuationToken: String?): Result<SearchResult> {
        if (continuationToken != null) {
            return resultForQueryWithToken["$query|$continuationToken"] ?: resultToReturn
        }
        return resultForQuery[query] ?: resultToReturn
    }
}

class FakeSearchAddToWatchLaterUseCase : AddToWatchLaterUseCase(
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

class FakeSearchVisitorDataBootstrapper : VisitorDataBootstrapper(
    httpClient = okhttp3.OkHttpClient()
) {
    var shouldSucceed = true

    override suspend fun ensureVisitorData(): Boolean = shouldSucceed
}
