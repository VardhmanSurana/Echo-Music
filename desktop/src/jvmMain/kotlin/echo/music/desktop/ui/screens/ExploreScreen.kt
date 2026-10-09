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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
          contentPadding = PaddingValues(vertical = 8.dp),
        ) {
          if (state.newReleases.isNotEmpty()) {
            item {
              SectionRow(
                title = "New releases",
                items = state.newReleases,
                onSongClick = { holder.playSong(it) },
                onOpenItem = { holder.openItem(it) },
                modifier = Modifier.padding(vertical = 8.dp),
              )
            }
          }
          items(state.moodsAndGenres.size) { index ->
            val section = state.moodsAndGenres[index]
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
              Text(
                text = section.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 12.dp),
              )
              Spacer(modifier = Modifier.height(8.dp))
              FlowRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                section.items.forEach { item ->
                  val chipColor = Color(item.stripeColor or 0xFF000000L)
                  Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier =
                      Modifier.clip(RoundedCornerShape(6.dp))
                        .background(chipColor)
                        .clickable {}
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                  )
                }
              }
            }
          }
          items(state.charts.size) { index ->
            val section = state.charts[index]
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
