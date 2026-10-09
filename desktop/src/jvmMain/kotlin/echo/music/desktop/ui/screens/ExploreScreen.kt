package echo.music.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.pages.ChartsPage
import com.music.innertube.pages.ExplorePage
import com.music.innertube.pages.MoodAndGenres
import echo.music.desktop.playback.DesktopPlaybackResolver
import echo.music.desktop.ui.components.EmptyState
import echo.music.desktop.ui.components.SectionRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreUiState(
  val loading: Boolean = true,
  val newReleases: List<AlbumItem> = emptyList(),
  val moodsAndGenres: List<MoodAndGenres> = emptyList(),
  val charts: List<ChartsPage.ChartSection> = emptyList(),
  val error: String? = null,
) {
  val isEmpty: Boolean
    get() = newReleases.isEmpty() && moodsAndGenres.isEmpty() && charts.isEmpty()
}

class ExploreStateHolder(
  private val scope: CoroutineScope,
  private val resolver: DesktopPlaybackResolver,
) {
  private val _state = MutableStateFlow(ExploreUiState())
  val state: StateFlow<ExploreUiState> = _state.asStateFlow()

  private var loadJob: Job? = null

  fun refresh() {
    loadJob?.cancel()
    loadJob = scope.launch {
      _state.value = ExploreUiState(loading = true)
      val explorePage: ExplorePage? = YouTube.explore().getOrNull()
      val moodSections: List<MoodAndGenres>? = YouTube.moodAndGenres().getOrNull()
      val chartsPage: ChartsPage? = YouTube.getChartsPage().getOrNull()
      if (explorePage == null && moodSections == null && chartsPage == null) {
        _state.value = ExploreUiState(loading = false, error = "Failed to load explore")
      } else {
        _state.value =
          ExploreUiState(
            loading = false,
            newReleases = explorePage?.newReleaseAlbums.orEmpty(),
            moodsAndGenres = moodSections.orEmpty(),
            charts = chartsPage?.sections.orEmpty(),
          )
      }
    }
  }

  fun playSong(song: SongItem) = resolver.playSong(song)

  fun openItem(item: YTItem) {
    scope.launch { resolver.openAndPlay(item) }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExploreScreen(
  holder: ExploreStateHolder,
  modifier: Modifier = Modifier,
) {
  val state by holder.state.collectAsState()
  LaunchedEffect(holder) { holder.refresh() }
  Column(modifier = modifier.fillMaxSize()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(text = "Explore & Charts", style = MaterialTheme.typography.headlineSmall)
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = { holder.refresh() }) {
        Icon(Icons.Default.Refresh, contentDescription = "Refresh explore")
      }
    }
    when {
      state.loading -> LoadingContent()
      state.error != null ->
        EmptyState(
          icon = Icons.Default.CloudOff,
          title = "Couldn't load Explore",
          description = state.error ?: "",
          action = { TextButton(onClick = { holder.refresh() }) { Text("Retry") } },
        )
      state.isEmpty ->
        EmptyState(
          icon = Icons.Default.CloudOff,
          title = "Nothing to explore right now",
          description = "New releases, moods and charts will appear here when available.",
        )
      else ->
        LazyColumn(
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(bottom = 28.dp),
        ) {
          if (state.newReleases.isNotEmpty()) {
            item {
              SectionRow(
                title = "New releases",
                items = state.newReleases,
                onSongClick = { holder.playSong(it) },
                onOpenItem = { holder.openItem(it) },
                modifier = Modifier.padding(vertical = 10.dp),
              )
            }
          }

          items(state.charts.size) { index ->
            val section = state.charts[index]
            val songItems = section.items.filterIsInstance<SongItem>()
            if (songItems.isNotEmpty() && songItems.size >= 4) {
              Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = section.title,
                    style =
                      MaterialTheme.typography.titleLarge.copy(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                      ),
                  )
                  Spacer(modifier = Modifier.weight(1f))
                  TextButton(onClick = { holder.playSong(songItems.first()) }) {
                    Text("Play all", style = MaterialTheme.typography.labelMedium)
                  }
                }

                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerLow,
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    songItems.take(5).forEachIndexed { rank, song ->
                      ChartLeaderboardRow(
                        rank = rank + 1,
                        song = song,
                        onClick = { holder.playSong(song) },
                      )
                    }
                  }
                }
              }
            } else {
              SectionRow(
                title = section.title,
                items = section.items,
                onSongClick = { holder.playSong(it) },
                onOpenItem = { holder.openItem(it) },
                modifier = Modifier.padding(vertical = 10.dp),
              )
            }
          }

          items(state.moodsAndGenres.size) { index ->
            val section = state.moodsAndGenres[index]
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
              Text(
                text = section.title,
                style =
                  MaterialTheme.typography.titleLarge.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                  ),
              )
              Spacer(modifier = Modifier.height(12.dp))
              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                section.items.forEach { item ->
                  MoodTagChip(item = item)
                }
              }
            }
          }
        }
    }
  }
}

@Composable
private fun ChartLeaderboardRow(
  rank: Int,
  song: SongItem,
  onClick: () -> Unit,
) {
  var isHovered by remember { mutableStateOf(false) }

  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(
          if (isHovered) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
          else Color.Transparent
        )
        .pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              when (event.type) {
                androidx.compose.ui.input.pointer.PointerEventType.Enter -> isHovered = true
                androidx.compose.ui.input.pointer.PointerEventType.Exit -> isHovered = false
              }
            }
          }
        }
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = rank.toString(),
      style =
        MaterialTheme.typography.titleMedium.copy(
          fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        ),
      color =
        if (rank <= 3) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.width(28.dp),
    )

    AsyncImage(
      model = song.thumbnail,
      contentDescription = song.title,
      contentScale = androidx.compose.ui.layout.ContentScale.Crop,
      modifier =
        Modifier.size(44.dp)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )

    Spacer(modifier = Modifier.width(12.dp))

    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = song.title,
        style =
          MaterialTheme.typography.bodyMedium.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
          ),
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
      )
      val artist = song.artists.joinToString(", ") { it.name }
      if (artist.isNotBlank()) {
        Text(
          text = artist,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun MoodTagChip(item: MoodAndGenres.Item) {
  val baseColor = Color(item.stripeColor or 0xFF000000L)
  var isHovered by remember { mutableStateOf(false) }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = baseColor.copy(alpha = if (isHovered) 1f else 0.85f),
    shadowElevation = if (isHovered) 3.dp else 0.dp,
    modifier =
      Modifier.pointerInput(Unit) {
          awaitPointerEventScope {
            while (true) {
              val event = awaitPointerEvent()
              when (event.type) {
                androidx.compose.ui.input.pointer.PointerEventType.Enter -> isHovered = true
                androidx.compose.ui.input.pointer.PointerEventType.Exit -> isHovered = false
              }
            }
          }
        }
        .clickable {},
  ) {
    Text(
      text = item.title,
      style =
        MaterialTheme.typography.labelLarge.copy(
          fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
        ),
      color = Color.White,
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
    )
  }
}
