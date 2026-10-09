package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import echo.music.desktop.auth.AuthSyncState
import echo.music.desktop.playback.DesktopPlaybackResolver
import echo.music.desktop.ui.components.EmptyState
import echo.music.desktop.ui.components.MediaCard
import echo.music.desktop.ui.components.TrackList
import echo.music.desktop.ui.components.TrackUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
  PLAYLISTS("Playlists"),
  LIKED_SONGS("Liked Songs"),
}

data class LibraryUiState(
  val loading: Boolean = false,
  val synced: Boolean = false,
  val playlists: List<PlaylistItem> = emptyList(),
  val likedSongs: List<SongItem> = emptyList(),
  val error: String? = null,
)

class LibraryStateHolder(
  private val scope: CoroutineScope,
  private val resolver: DesktopPlaybackResolver,
) {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state.asStateFlow()

  fun refresh() {
    scope.launch {
      val synced = AuthSyncState.status.value.synced
      if (!synced) {
        _state.value = LibraryUiState(synced = false)
        return@launch
      }
      _state.value = LibraryUiState(loading = true, synced = true)
      val playlists =
        YouTube.library("FEmusic_liked_playlists")
          .getOrNull()
          ?.items
          ?.filterIsInstance<PlaylistItem>()
      val likedSongs =
        YouTube.library("FEmusic_liked_videos").getOrNull()?.items?.filterIsInstance<SongItem>()
      _state.value =
        LibraryUiState(
          synced = true,
          playlists = playlists ?: emptyList(),
          likedSongs = likedSongs ?: emptyList(),
          error =
            if (playlists == null && likedSongs == null) "Failed to load your library" else null,
        )
    }
  }

  fun playSong(song: SongItem) = resolver.playSong(song)

  fun playLikedSongs(startIndex: Int) {
    resolver.playQueue(_state.value.likedSongs, startIndex)
  }

  fun openItem(item: YTItem) {
    scope.launch { resolver.openAndPlay(item) }
  }
}

@Composable
fun LibraryScreen(
  holder: LibraryStateHolder,
  modifier: Modifier = Modifier,
  initialTab: LibraryTab = LibraryTab.PLAYLISTS,
) {
  val state by holder.state.collectAsState()
  val syncStatus by AuthSyncState.status.collectAsState()
  var selectedTab by remember { mutableIntStateOf(initialTab.ordinal) }

  LaunchedEffect(syncStatus.synced) { holder.refresh() }

  Column(modifier = modifier.fillMaxSize()) {
    SecondaryTabRow(selectedTabIndex = selectedTab, modifier = Modifier.fillMaxWidth()) {
      LibraryTab.entries.forEachIndexed { index, tab ->
        Tab(
          selected = selectedTab == index,
          onClick = { selectedTab = index },
          text = { Text(tab.label) },
        )
      }
    }
    when {
      !syncStatus.synced ->
        EmptyState(
          icon = Icons.Default.Extension,
          title = "Sign in via the browser extension",
          description =
            "Install the Echo Music companion extension and load it unpacked via " +
              "chrome://extensions to sync your YouTube Music library. You can also paste a " +
              "Cookie header manually from Account & Sync in the menu bar.",
        )
      state.loading -> LoadingContent()
      state.error != null ->
        EmptyState(
          icon = Icons.Default.CloudOff,
          title = "Couldn't load library",
          description = state.error ?: "",
        )
      selectedTab == LibraryTab.PLAYLISTS.ordinal ->
        if (state.playlists.isEmpty()) {
          EmptyState(
            icon = Icons.Default.Extension,
            title = "No playlists yet",
            description = "Playlists saved to your YouTube Music library will show up here.",
          )
        } else {
          LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            contentPadding = PaddingValues(12.dp),
            modifier = Modifier.fillMaxSize(),
          ) {
            items(state.playlists, key = { it.id }) { playlist ->
              MediaCard(item = playlist, onClick = { holder.openItem(playlist) })
            }
          }
        }
      else ->
        if (state.likedSongs.isEmpty()) {
          EmptyState(
            icon = Icons.Default.Extension,
            title = "No liked songs yet",
            description = "Songs you like on YouTube Music will show up here.",
          )
        } else {
          TrackList(
            tracks = state.likedSongs.map { TrackUi.from(it) },
            onPlay = { holder.playLikedSongs(it) },
            artworkSize = 36.dp,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
          )
        }
    }
  }
}
