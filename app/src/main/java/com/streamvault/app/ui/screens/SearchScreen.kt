package com.streamvault.app.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamvault.app.settings.SettingsManager
import com.streamvault.app.ui.StreamVaultState
import com.streamvault.app.ui.theme.*
import com.streamvault.app.webview.YouTubeWebView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var currentSearchUrl by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    val recentSearches = remember {
        mutableStateListOf<String>().apply {
            addAll(SettingsManager.recentSearches)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Search",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DarkBackground
            ),
            windowInsets = WindowInsets(0, 0, 0, 0)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = DarkCard
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        "Search YouTube...",
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    if (isSearching) {
                        IconButton(onClick = {
                            isSearching = false
                            currentSearchUrl = null
                            searchQuery = ""
                        }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back to search",
                                tint = TextPrimary
                            )
                        }
                    } else {
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    }
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Clear search",
                                tint = TextMuted
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.textFieldColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        performSearch(
                            query = searchQuery,
                            recentSearches = recentSearches,
                            onUrlReady = { url ->
                                currentSearchUrl = url
                                isSearching = true
                            },
                            focusManager = focusManager
                        )
                    }
                )
            )
        }

        if (isSearching) {
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Accent,
                    trackColor = DarkBackground
                )
            }

            currentSearchUrl?.let { url ->
                Box(modifier = Modifier.weight(1f)) {
                    YouTubeWebView(
                        url = url,
                        modifier = Modifier.fillMaxSize(),
                        adBlockingEnabled = SettingsManager.adBlockingEnabled,
                        sponsorBlockEnabled = SettingsManager.sponsorBlockEnabled,
                        backgroundPlayEnabled = SettingsManager.backgroundPlayEnabled,
                        onProgressChanged = { progress = it },
                        webViewRef = { StreamVaultState.setWebView(2, it) }
                    )
                }
            }
        } else {
            if (recentSearches.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Searches",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    TextButton(onClick = {
                        SettingsManager.clearAllSearches()
                        recentSearches.clear()
                    }) {
                        Text(
                            "Clear all",
                            color = Accent,
                            fontSize = 13.sp
                        )
                    }
                }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recentSearches.toList()) { search ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    searchQuery = search
                                    performSearch(
                                        query = search,
                                        recentSearches = recentSearches,
                                        onUrlReady = { url ->
                                            currentSearchUrl = url
                                            isSearching = true
                                        },
                                        focusManager = focusManager
                                    )
                                }
                                .padding(vertical = 14.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = search,
                                fontSize = 15.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    SettingsManager.removeRecentSearch(search)
                                    recentSearches.remove(search)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove $search from history",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextMuted.copy(alpha = 0.3f),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Search YouTube",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Find videos, music, channels and more",
                            fontSize = 14.sp,
                            color = TextMuted.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

private fun performSearch(
    query: String,
    recentSearches: MutableList<String>,
    onUrlReady: (String) -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    if (query.isBlank()) return
    SettingsManager.addRecentSearch(query)
    if (!recentSearches.contains(query)) {
        recentSearches.add(0, query)
        if (recentSearches.size > 15) recentSearches.removeLast()
    }
    onUrlReady("https://m.youtube.com/results?search_query=${Uri.encode(query)}")
    focusManager.clearFocus()
}
