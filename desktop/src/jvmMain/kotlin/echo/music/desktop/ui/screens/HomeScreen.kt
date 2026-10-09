package echo.music.desktop.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.PlaylistItem
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

  data class Content(
    val chips: List<HomePage.Chip> = emptyList(),
    val sections: List<HomePage.Section>,
  ) : HomeUiState
}

class HomeStateHolder(
  private val scope: CoroutineScope,
  private val resolver: DesktopPlaybackResolver,
) {
  private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
  val state: StateFlow<HomeUiState> = _state.asStateFlow()

  private var loadJob: Job? = null
  private var selectedChip: HomePage.Chip? = null

  fun refresh() {
    loadJob?.cancel()
    loadJob = scope.launch {
      _state.value = HomeUiState.Loading
      YouTube.home()
        .fold(
          onSuccess = { page ->
            _state.value = HomeUiState.Content(page.chips.orEmpty(), page.sections)
          },
          onFailure = { _state.value = HomeUiState.Error(it.message ?: "Failed to load home") },
        )
    }
  }

  fun selectChip(chip: HomePage.Chip?) {
    selectedChip = chip
    val endpoint = chip?.endpoint
    if (endpoint == null) {
      refresh()
      return
    }
    loadJob?.cancel()
    loadJob = scope.launch {
      _state.value = HomeUiState.Loading
      YouTube.browse(endpoint.browseId, endpoint.params)
        .fold(
          onSuccess = { result ->
            val sections =
              result.items.map { item ->
                HomePage.Section(
                  title = item.title ?: chip.title,
                  label = null,
                  thumbnail = null,
                  endpoint = null,
                  items = item.items,
                )
              }
            _state.value =
              HomeUiState.Content((_state.value as? HomeUiState.Content)?.chips.orEmpty(), sections)
          },
          onFailure = { refresh() },
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
  var activeChipTitle by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(holder) { holder.refresh() }

  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier =
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = "Home",
        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
      )
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
      is HomeUiState.Content -> {
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 24.dp),
        ) {
          if (current.chips.isNotEmpty()) {
            item {
              LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                item {
                  FilterChip(
                    selected = activeChipTitle == null,
                    onClick = {
                      activeChipTitle = null
                      holder.selectChip(null)
                    },
                    label = { Text("All", fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(20.dp),
                  )
                }
                items(current.chips, key = { it.title }) { chip ->
                  val isSelected = activeChipTitle == chip.title
                  FilterChip(
                    selected = isSelected,
                    onClick = {
                      if (isSelected) {
                        activeChipTitle = null
                        holder.selectChip(null)
                      } else {
                        activeChipTitle = chip.title
                        holder.selectChip(chip)
                      }
                    },
                    label = { Text(chip.title, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(20.dp),
                  )
                }
              }
            }
          }

          val firstSection = current.sections.firstOrNull()
          val heroItem = firstSection?.items?.firstOrNull()
          if (heroItem != null) {
            item {
              HomeHeroBanner(
                item = heroItem,
                subtitlePrefix = firstSection.title,
                onPlay = {
                  if (heroItem is SongItem) holder.playSong(heroItem) else holder.openItem(heroItem)
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
              )
            }
          }

          items(current.sections, key = { it.title }) { section ->
            SectionRow(
              title = section.title,
              items = section.items,
              onSongClick = { holder.playSong(it) },
              onOpenItem = { holder.openItem(it) },
              modifier = Modifier.padding(vertical = 10.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HomeHeroBanner(
  item: YTItem,
  subtitlePrefix: String,
  onPlay: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val subtitle =
    when (item) {
      is SongItem -> item.artists.joinToString(", ") { it.name }
      is AlbumItem -> item.artists?.joinToString(", ") { it.name } ?: "Album"
      is PlaylistItem -> item.author?.name ?: "Playlist"
      else -> subtitlePrefix
    }

  Surface(
    modifier =
      modifier
        .fillMaxWidth()
        .height(220.dp)
        .clip(RoundedCornerShape(16.dp))
        .clickable(onClick = onPlay),
    color = MaterialTheme.colorScheme.surfaceContainerHighest,
    tonalElevation = 4.dp,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      AsyncImage(
        model = item.thumbnail,
        contentDescription = item.title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )

      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(
              Brush.horizontalGradient(
                colors =
                  listOf(
                    Color.Black.copy(alpha = 0.85f),
                    Color.Black.copy(alpha = 0.55f),
                    Color.Transparent,
                  )
              )
            )
      )

      Row(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp),
          ) {
            Text(
              text = subtitlePrefix.uppercase(),
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
          }
          Text(
            text = item.title,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
          if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White.copy(alpha = 0.8f),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primary,
          shadowElevation = 6.dp,
          modifier = Modifier.size(56.dp).clickable(onClick = onPlay),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              Icons.Default.PlayArrow,
              contentDescription = "Play",
              tint = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(32.dp),
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
