---
title: 'Fix LibraryScreen - Make Playlists, Watch History, and Downloads Fully Functional'
type: 'feature'
created: '2026-07-18'
status: 'draft'
review_loop_iteration: 0
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** LibraryScreen.kt currently shows empty placeholders for playlists and watch history, and the downloads section lacks several important features: no thumbnails, no status indicators, no file size display, no pause/resume functionality, and no error state handling. The screen needs to be fully functional with real data from Room DB and proper UI interactions.

**Approach:** Enhance LibraryScreen.kt to display real data from Room DB for playlists and watch history, improve the downloads section with thumbnails, status badges, file size display, and pause/resume buttons, add error state handling, and implement all required UI interactions using existing ViewModel methods and Compose/Material 3 patterns.

## Boundaries & Constraints

**Always:**
- Use existing Compose/Material 3 patterns from the codebase
- Keep AMOLED theme (pure black backgrounds, hot pink accents)
- Use existing ViewModel methods where they exist
- Add new ViewModel methods only when needed
- Do NOT create new files unless absolutely necessary
- Test by building: .\gradlew.bat assembleDebug

**Ask First:**
- Whether to implement watch history using DataStore or Room (need to decide)
- Whether to add a new WatchHistoryEntity to Room DB

**Never:**
- Do not break existing functionality
- Do not use external libraries not already in the project
- Do not modify the project structure outside of LibraryScreen.kt and LibraryViewModel.kt

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| Downloads with thumbnails | DownloadEntity with thumbnailUrl | Show thumbnail using Coil AsyncImage | Fallback to generic play icon |
| Download status badges | downloadStatus = "COMPLETED", "DOWNLOADING", "FAILED" | Green badge for completed, blue for downloading, red for failed | Show appropriate badge color |
| Clear all downloads | User clicks "Clear All Downloads" button | Call viewModel.clearCompleted() | Show error message if fails |
| Pause download | User clicks pause button on downloading item | Call viewModel.pauseDownload(videoId) | Show error message if fails |
| Resume download | User clicks resume button on paused item | Call viewModel.resumeDownload(videoId) | Show error message if fails |
| File size display | DownloadEntity with fileSize | Format as human-readable (KB/MB/GB) | Show "Unknown" if 0 |
| Create playlist | User clicks "Create Playlist" button | Show dialog to enter name, call viewModel.createPlaylist() | Show error message if fails |
| Delete playlist | User clicks delete on playlist | Call viewModel.deletePlaylist() | Show error message if fails |
| Watch history display | Recently watched videos | Show list of videos with thumbnail, title, channel | Show empty state if no history |
| Error state | uiState.error is non-null | Display error message in UI | Provide dismiss option |

</frozen-after-approval>

## Code Map

- `app/src/main/java/com/freedomplay/app/presentation/ui/screens/library/LibraryScreen.kt` -- Main UI file to be modified
- `app/src/main/java/com/freedomplay/app/presentation/ui/screens/library/LibraryViewModel.kt` -- ViewModel to be enhanced with playlist and watch history support
- `app/src/main/java/com/freedomplay/app/data/local/db/DownloadEntity.kt` -- Download entity with thumbnailUrl and fileSize fields
- `app/src/main/java/com/freedomplay/app/data/local/db/PlaylistEntity.kt` -- Playlist entity for Room DB
- `app/src/main/java/com/freedomplay/app/data/local/db/PlaylistVideoEntity.kt` -- Playlist video entity for Room DB
- `app/src/main/java/com/freedomplay/app/data/local/db/PlaylistDao.kt` -- DAO for playlist operations
- `app/src/main/java/com/freedomplay/app/download/DownloadManager.kt` -- Download manager with pause/resume methods
- `app/src/main/java/com/freedomplay/app/di/DatabaseModule.kt` -- Hilt module providing DAOs

## Tasks & Acceptance

**Execution:**
- [ ] `LibraryViewModel.kt` -- Add playlist operations (create, delete, get playlists) by injecting PlaylistDao
- [ ] `LibraryViewModel.kt` -- Add watch history support (need to decide on implementation approach)
- [ ] `LibraryViewModel.kt` -- Add pauseDownload() and resumeDownload() methods
- [ ] `LibraryViewModel.kt` -- Update LibraryUiState to include playlists and watch history
- [ ] `LibraryScreen.kt` -- Add Coil AsyncImage to display thumbnails on download items
- [ ] `LibraryScreen.kt` -- Add download status badges with colored indicators
- [ ] `LibraryScreen.kt` -- Add "Clear All Downloads" button wired to viewModel.clearCompleted()
- [ ] `LibraryScreen.kt` -- Add pause/resume buttons for downloads
- [ ] `LibraryScreen.kt` -- Show file size formatted as human-readable
- [ ] `LibraryScreen.kt` -- Implement Playlists section with create/delete functionality
- [ ] `LibraryScreen.kt` -- Implement Watch History section
- [ ] `LibraryScreen.kt` -- Show error state when uiState.error is non-null

**Acceptance Criteria:**
- Given a download with a thumbnailUrl, when displayed, then show the thumbnail using Coil AsyncImage
- Given a download with status "COMPLETED", when displayed, then show green status badge
- Given a download with status "DOWNLOADING", when displayed, then show blue status badge and progress
- Given a download with status "FAILED", when displayed, then show red status badge
- Given a download with fileSize > 0, when displayed, then show human-readable file size (KB/MB/GB)
- Given user clicks "Clear All Downloads", when confirmed, then call viewModel.clearCompleted()
- Given user clicks pause on a downloading item, when clicked, then pause the download
- Given user clicks resume on a paused item, when clicked, then resume the download
- Given user clicks "Create Playlist", when entered name and confirmed, then create new playlist in Room DB
- Given playlists exist, when displayed, then show list of playlists with name and video count
- Given user clicks delete on playlist, when confirmed, then delete playlist from Room DB
- Given recently watched videos exist, when displayed, then show list with thumbnail, title, channel
- Given uiState.error is non-null, when displayed, then show error message with dismiss option

## Spec Change Log

## Design Notes

The implementation will follow existing patterns in the codebase:
- Use Coil's AsyncImage for thumbnail loading (already in dependencies)
- Use Material 3 Card components for consistent styling
- Use existing EmptyState component for empty states
- Add new composables only when necessary to keep the file manageable
- Follow the existing MVVM pattern with ViewModel handling business logic

## Verification

**Commands:**
- `.\gradlew.bat assembleDebug` -- expected: BUILD SUCCESSFUL
- Manual testing: Navigate to Library screen and verify all sections work correctly

**Manual checks (if no CLI):**
- Verify thumbnails load correctly on download items
- Verify status badges show correct colors
- Verify file size displays correctly
- Verify pause/resume buttons work
- Verify playlist creation and deletion
- Verify watch history display
- Verify error state shows when appropriate