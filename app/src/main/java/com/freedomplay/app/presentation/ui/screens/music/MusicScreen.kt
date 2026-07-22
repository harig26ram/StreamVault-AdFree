package com.freedomplay.app.presentation.ui.screens.music

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.freedomplay.app.domain.model.MusicSection
import com.freedomplay.app.domain.model.StreamItem
import com.freedomplay.app.presentation.viewmodel.MusicViewModel

/**
 * Ad-free YouTube Music clone: one Music destination with Home / Explore / Library tabs.
 * Home and Explore render the real YT Music shelves (WEB_REMIX InnerTube, personalized when
 * signed in); Library is the locally recorded listening history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    onVideoClick: (StreamItem) -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Home", "Explore", "Library")

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = "Music", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTab == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> MusicHomeTab(viewModel, onVideoClick)
            1 -> MusicExploreTab(viewModel, onVideoClick)
            2 -> MusicLibraryTab(viewModel, onVideoClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicHomeTab(
    viewModel: MusicViewModel,
    onVideoClick: (StreamItem) -> Unit
) {
    val sections by viewModel.homeSections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (sections.isEmpty()) viewModel.loadMusicHome()
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { viewModel.loadMusicHome() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && sections.isEmpty() -> MusicLoadingShimmer()
            error != null && sections.isEmpty() -> MusicErrorState(
                message = error ?: "Something went wrong",
                onRetry = { viewModel.loadMusicHome() }
            )
            else -> MusicSectionList(sections, onVideoClick)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicExploreTab(
    viewModel: MusicViewModel,
    onVideoClick: (StreamItem) -> Unit
) {
    val sections by viewModel.exploreSections.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (sections.isEmpty()) viewModel.loadMusicExplore()
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = { viewModel.loadMusicExplore() },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            isLoading && sections.isEmpty() -> MusicLoadingShimmer()
            error != null && sections.isEmpty() -> MusicErrorState(
                message = error ?: "Something went wrong",
                onRetry = { viewModel.loadMusicExplore() }
            )
            else -> MusicSectionList(sections, onVideoClick)
        }
    }
}

@Composable
private fun MusicSectionList(
    sections: List<MusicSection>,
    onVideoClick: (StreamItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        sections.forEachIndexed { index, section ->
            item(key = "${index}_${section.title}") {
                MusicCarousel(
                    title = section.title,
                    items = section.items,
                    onVideoClick = onVideoClick,
                    itemSize = if (index == 0) 160 else 140
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun MusicLibraryTab(
    viewModel: MusicViewModel,
    onVideoClick: (StreamItem) -> Unit
) {
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()

    if (libraryItems.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nothing here yet — songs and videos you play will show up here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp)
    ) {
        item {
            Text(
                text = "Recently played",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        items(libraryItems, key = { it.videoId }) { video ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onVideoClick(video) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = video.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = video.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = video.uploaderName,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicLoadingShimmer() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )

    Column(modifier = Modifier.padding(16.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha.value))
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha.value))
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MusicErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Retry")
        }
    }
}
