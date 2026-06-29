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
import com.streamvault.app.ui.StreamVaultApp
import com.streamvault.app.ui.theme.*
import com.streamvault.app.webview.YouTubeWebView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    var searchQuery by remember { mutableStateOf(SettingsManager.lastSearchQuery) }
    var isSearching by remember { mutableStateOf(false) }
    var currentSearchUrl by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current

    val recentSearches = remember {
        mutableStateListOf(
            "lofi hip hop radio",
            "coding tutorial python",
            "funny cat videos",
            "4k nature documentary",
            "jazz music playlist"
        )
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
            )
        )

        if (!isSearching) {
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
                        Icon(
                            Icons.Filled.Search,
                            contentDescription = null,
                            tint = TextMuted
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear",
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
                            if (searchQuery.isNotBlank()) {
                                SettingsManager.lastSearchQuery = searchQuery
                                if (!recentSearches.contains(searchQuery)) {
                                    recentSearches.add(0, searchQuery)
                                    if (recentSearches.size > 10) recentSearches.removeLast()
                                }
                                currentSearchUrl = "https://m.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
                                isSearching = true
                                focusManager.clearFocus()
                            }
                        }
                    )
                )
            }

            Text(
                text = "Recent Searches",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(recentSearches) { search ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                searchQuery = search
                                SettingsManager.lastSearchQuery = search
                                currentSearchUrl = "https://m.youtube.com/results?search_query=${Uri.encode(search)}"
                                isSearching = true
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
                        Icon(
                            imageVector = Icons.Filled.NorthWest,
                            contentDescription = "Insert",
                            tint = TextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else {
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
                        IconButton(onClick = {
                            isSearching = false
                            currentSearchUrl = null
                        }) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear",
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
                            if (searchQuery.isNotBlank()) {
                                SettingsManager.lastSearchQuery = searchQuery
                                if (!recentSearches.contains(searchQuery)) {
                                    recentSearches.add(0, searchQuery)
                                    if (recentSearches.size > 10) recentSearches.removeLast()
                                }
                                currentSearchUrl = "https://m.youtube.com/results?search_query=${Uri.encode(searchQuery)}"
                                focusManager.clearFocus()
                            }
                        }
                    )
                )
            }

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
                YouTubeWebView(
                    url = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onProgressChanged = { progress = it },
                    webViewRef = { StreamVaultApp.currentWebView = it }
                )
            }
        }
    }
}
