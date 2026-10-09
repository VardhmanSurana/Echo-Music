package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.HomePage
import echo.music.desktop.playback.DesktopPlaybackResolver
import echo.music.desktop.ui.components.EmptyState
import echo.music.desktop.ui.components.SectionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
  data object Loading : HomeUiState

  data class Error(val message: String) : HomeUiState

  data class Content(val sections: List<HomePage.Section>) : HomeUiState
}

class HomeStateHolder(
  private val scope: CoroutineScope,
  private val resolver: DesktopPlaybackResolver,
) {
  private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
  val state: StateFlow<HomeUiState> = _state.asStateFlow()

  private var loadJob: Job? = null

  fun refresh() {
    loadJob?.cancel()
    loadJob = scope.launch {
      _state.value = HomeUiState.Loading
      YouTube.home()
        .fold(
          onSuccess = { page -> _state.value = HomeUiState.Content(page.sections) },
          onFailure = { _state.value = HomeUiState.Error(it.message ?: "Failed to load home") },
        )
    }
  }

  fun playSong(song: SongItem) = resolver.playSong(song)

  fun openItem(item: YTItem) {
    scope.launch { resolver.openAndPlay(item) }
  }
}

@Composable
fun HomeScreen(
  holder: HomeStateHolder,
  modifier: Modifier = Modifier,
) {
  val state by holder.state.collectAsState()
  LaunchedEffect(holder) { holder.refresh() }
  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = "Home", style = MaterialTheme.typography.headlineSmall)
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = { holder.refresh() }) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh home")
      }
    }
    when (val current = state) {
      HomeUiState.Loading -> LoadingContent()
      is HomeUiState.Error ->
        EmptyState(
          icon = Icons.Default.CloudOff,
          title = "Couldn't load Home",
          description = current.message,
          action = {
            TextButton(onClick = { holder.refresh() }) { Text("Retry") }
          },
        )
      is HomeUiState.Content ->
        if (current.sections.isEmpty()) {
          EmptyState(
            icon = Icons.Default.CloudOff,
            title = "Nothing here yet",
            description = "Home recommendations will appear once YouTube Music returns content.",
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
          ) {
            items(current.sections, key = { it.title }) { section ->
              SectionRow(
                title = section.title,
                items = section.items,
                onSongClick = { holder.playSong(it) },
                onOpenItem = { holder.openItem(it) },
                modifier = Modifier.padding(vertical = 8.dp),
              )
            }
          }
        }
    }
  }
}

@Composable
internal fun LoadingContent() {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}
