package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.models.SongItem
import echo.music.desktop.local.LocalAudioFile

data class TrackUi(
  val title: String,
  val artist: String,
  val album: String,
  val durationMs: Long,
  val artworkUrl: String? = null,
) {
  companion object {
    fun from(song: SongItem) =
      TrackUi(
        title = song.title,
        artist = song.artists.joinToString(", ") { it.name },
        album = song.album?.name.orEmpty(),
        durationMs = (song.duration ?: 0) * 1000L,
        artworkUrl = song.thumbnail,
      )

    fun from(file: LocalAudioFile) =
      TrackUi(
        title = file.title,
        artist = file.artist.orEmpty(),
        album = file.album.orEmpty(),
        durationMs = file.durationMs,
      )
  }
}

fun formatDuration(durationMs: Long): String {
  if (durationMs <= 0L) return "-"
  val totalSeconds = durationMs / 1000
  val hours = totalSeconds / 3600
  val minutes = (totalSeconds % 3600) / 60
  val seconds = totalSeconds % 60
  return if (hours > 0) {
    String.format("%d:%02d:%02d", hours, minutes, seconds)
  } else {
    String.format("%d:%02d", minutes, seconds)
  }
}

@Composable
fun TrackList(
  tracks: List<TrackUi>,
  onPlay: (Int) -> Unit,
  modifier: Modifier = Modifier,
  showHeader: Boolean = true,
  artworkSize: Dp = 0.dp,
) {
  var selectedIndex by remember(tracks) { mutableIntStateOf(-1) }
  Column(modifier = modifier) {
    if (showHeader) {
      TrackRow(
        track =
          TrackUi(
            title = "Title",
            artist = "Artist",
            album = "Album",
            durationMs = 0L,
          ),
        isHeader = true,
        onClick = {},
        onDoubleClick = {},
      )
    }
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
      itemsIndexed(tracks, key = { index, track -> "$index:${track.title}" }) { index, track ->
        TrackRow(
          track = track,
          selected = index == selectedIndex,
          artworkSize = artworkSize,
          onClick = { selectedIndex = index },
          onDoubleClick = { onPlay(index) },
        )
      }
    }
  }
}

@Composable
private fun TrackRow(
  track: TrackUi,
  onClick: () -> Unit,
  onDoubleClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  isHeader: Boolean = false,
  artworkSize: Dp = 0.dp,
) {
  val textStyle =
    if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
  val textColor =
    if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .background(
          when {
            isHeader -> MaterialTheme.colorScheme.surfaceContainerHighest
            selected -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surface
          }
        )
        .pointerInput(track, onClick, onDoubleClick) {
          detectTapGestures(onTap = { onClick() }, onDoubleTap = { onDoubleClick() })
        }
        .padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (!isHeader && artworkSize > 0.dp) {
      Artwork(artworkUrl = track.artworkUrl, size = artworkSize)
    }
    TrackCell(text = track.title, weight = 3f, style = textStyle, color = textColor, maxLines = 1)
    TrackCell(
      text = track.artist,
      weight = 2f,
      style = textStyle,
      color = if (isHeader) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
    TrackCell(
      text = track.album,
      weight = 2f,
      style = textStyle,
      color = if (isHeader) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
    )
    Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
      Text(
        text = if (isHeader) "Duration" else formatDuration(track.durationMs),
        style = textStyle,
        color = textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun RowScope.TrackCell(
  text: String,
  weight: Float,
  style: TextStyle,
  color: Color,
  maxLines: Int,
) {
  Text(
    text = text,
    style = style,
    color = color,
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(weight).padding(end = 8.dp),
  )
}

@Composable
private fun Artwork(artworkUrl: String?, size: Dp) {
  AsyncImage(
    model = artworkUrl,
    contentDescription = null,
    modifier =
      Modifier.padding(end = 8.dp)
        .size(size)
        .clip(RoundedCornerShape(4.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
  )
}
