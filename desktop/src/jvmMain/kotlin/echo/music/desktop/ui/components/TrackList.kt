package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
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

    fun from(file: LocalAudioFile): TrackUi {
      val playbackItem = file.toPlaybackItem()
      return TrackUi(
        title = file.title,
        artist = file.artist.orEmpty(),
        album = file.album.orEmpty(),
        durationMs = file.durationMs,
        artworkUrl = playbackItem.artworkUrl,
      )
    }
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
  artworkSize: Dp = 48.dp,
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
        artworkSize = artworkSize,
        onClick = {},
      )
    }
    LazyColumn(
      modifier = Modifier.fillMaxWidth().weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      itemsIndexed(tracks, key = { index, track -> "$index:${track.title}" }) { index, track ->
        TrackRow(
          track = track,
          selected = index == selectedIndex,
          artworkSize = artworkSize,
          onClick = {
            selectedIndex = index
            onPlay(index)
          },
        )
      }
    }
  }
}

@Composable
private fun TrackRow(
  track: TrackUi,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  isHeader: Boolean = false,
  artworkSize: Dp = 48.dp,
) {
  var isHovered by remember { mutableStateOf(false) }

  val textStyle =
    if (isHeader) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
  val textColor =
    if (isHeader) MaterialTheme.colorScheme.onSurfaceVariant
    else MaterialTheme.colorScheme.onSurface

  val backgroundColor =
    when {
      isHeader -> Color.Transparent
      selected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
      isHovered -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f)
      else -> Color.Transparent
    }

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(backgroundColor)
        .then(
          if (!isHeader) {
            Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                  while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                      PointerEventType.Enter -> isHovered = true
                      PointerEventType.Exit -> isHovered = false
                    }
                  }
                }
              }
              .clickable(onClick = onClick)
          } else {
            Modifier
          }
        )
        .padding(horizontal = 12.dp, vertical = if (isHeader) 8.dp else 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (isHeader) {
      if (artworkSize > 0.dp) {
        Spacer(modifier = Modifier.width(artworkSize + 12.dp))
      }
      TrackCell(text = track.title, weight = 4f, style = textStyle, color = textColor, maxLines = 1)
      TrackCell(
        text = track.artist,
        weight = 3f,
        style = textStyle,
        color = textColor,
        maxLines = 1,
      )
      TrackCell(
        text = track.album,
        weight = 3f,
        style = textStyle,
        color = textColor,
        maxLines = 1,
      )
    } else {
      if (artworkSize > 0.dp) {
        Artwork(artworkUrl = track.artworkUrl, size = artworkSize, title = track.title)
        Spacer(modifier = Modifier.width(12.dp))
      }
      TrackCell(
        text = track.title,
        weight = 4f,
        style =
          textStyle.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
        color = if (selected) MaterialTheme.colorScheme.primary else textColor,
        maxLines = 1,
      )
      TrackCell(
        text = track.artist.ifBlank { "Unknown Artist" },
        weight = 3f,
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
      TrackCell(
        text = track.album.ifBlank { "—" },
        weight = 3f,
        style = textStyle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }

    Box(modifier = Modifier.width(64.dp), contentAlignment = Alignment.CenterEnd) {
      Text(
        text = if (isHeader) "Duration" else formatDuration(track.durationMs),
        style = textStyle,
        color = if (isHeader) textColor else MaterialTheme.colorScheme.onSurfaceVariant,
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
    modifier = Modifier.weight(weight).padding(end = 12.dp),
  )
}

@Composable
private fun Artwork(artworkUrl: String?, size: Dp, title: String) {
  if (!artworkUrl.isNullOrEmpty()) {
    AsyncImage(
      model = artworkUrl,
      contentDescription = title,
      contentScale = ContentScale.Crop,
      modifier =
        Modifier.size(size)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
  } else {
    Box(
      modifier =
        Modifier.size(size)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.secondaryContainer),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = title.firstOrNull()?.uppercase() ?: "♪",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}
