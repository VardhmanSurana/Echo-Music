package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.SearchSummary
import echo.music.desktop.playback.DesktopPlaybackResolver
import echo.music.desktop.ui.components.cardSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
  data object Idle : SearchUiState

  data object Loading : SearchUiState

  data class Results(val summaries: List<SearchSummary>) : SearchUiState {
    val isEmpty: Boolean
      get() = summaries.all { it.items.isEmpty() }
  }

  data class Error(val message: String) : SearchUiState
}

class SearchStateHolder(
  private val scope: CoroutineScope,
  private val resolver: DesktopPlaybackResolver,
) {
  private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
  val state: StateFlow<SearchUiState> = _state.asStateFlow()

  private var queryJob: Job? = null

  fun onQueryChanged(query: String) {
    queryJob?.cancel()
    val trimmed = query.trim()
    if (trimmed.isEmpty()) {
      _state.value = SearchUiState.Idle
      return
    }
    queryJob = scope.launch {
      _state.value = SearchUiState.Loading
      delay(SEARCH_DEBOUNCE_MS)
      YouTube.searchSummary(trimmed)
        .fold(
          onSuccess = { page -> _state.value = SearchUiState.Results(page.summaries) },
          onFailure = {
            _state.value = SearchUiState.Error(it.message ?: "Search failed")
          },
        )
    }
  }

  fun playSong(song: SongItem) = resolver.playSong(song)

  fun playFirstResult() {
    val firstSong =
      (_state.value as? SearchUiState.Results)?.summaries?.firstNotNullOfOrNull { summary ->
        summary.items.filterIsInstance<SongItem>().firstOrNull()
      }
    if (firstSong != null) {
      resolver.playSong(firstSong)
    }
  }

  companion object {
    private const val SEARCH_DEBOUNCE_MS = 400L
  }
}

@Composable
fun SearchOverlayContent(
  query: String,
  holder: SearchStateHolder,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  LaunchedEffect(query) { holder.onQueryChanged(query) }
  val state by holder.state.collectAsState()
  when (val current = state) {
    SearchUiState.Idle -> SearchHint("Type to search songs, albums and artists")
    SearchUiState.Loading ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Searching…",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    is SearchUiState.Error -> SearchHint(current.message)
    is SearchUiState.Results ->
      if (current.isEmpty) {
        SearchHint("No results for \"$query\"")
      } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
          current.summaries.forEach { summary ->
            if (summary.items.isEmpty()) return@forEach
            item(key = "header:${summary.title}") {
              Text(
                text = summary.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
              )
            }
            items(summary.items, key = { "${summary.title}:${it::class.simpleName}:${it.id}" }) {
              item ->
              SearchResultRow(
                item = item,
                onSongClick = {
                  if (item is SongItem) {
                    holder.playSong(item)
                    onClose()
                  }
                },
              )
            }
          }
        }
      }
  }
}

@Composable
private fun SearchHint(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
  )
}

@Composable
private fun SearchResultRow(item: YTItem, onSongClick: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(MaterialTheme.shapes.small)
        .clickable(onClick = onSongClick)
        .padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (item.thumbnail != null) {
      AsyncImage(
        model = item.thumbnail,
        contentDescription = null,
        modifier =
          Modifier.size(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      )
    } else {
      Icon(
        imageVector = Icons.Default.MusicNote,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Spacer(modifier = Modifier.width(10.dp))
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = item.title,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = item.cardSubtitle(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}
