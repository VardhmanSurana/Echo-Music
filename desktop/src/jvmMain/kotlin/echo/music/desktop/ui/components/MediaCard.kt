package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@Composable
fun MediaCard(
  title: String,
  subtitle: String,
  artworkUrl: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  width: Dp = 164.dp,
) {
  var isHovered by remember { mutableStateOf(false) }

  Column(
    modifier =
      modifier
        .width(width)
        .clip(RoundedCornerShape(12.dp))
        .background(
          if (isHovered) MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
          else Color.Transparent
        )
        .pointerInput(Unit) {
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
        .padding(8.dp)
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(10.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
      contentAlignment = Alignment.Center,
    ) {
      AsyncImage(
        model = artworkUrl,
        contentDescription = title,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
      )

      if (isHovered) {
        Box(
          modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)),
          contentAlignment = Alignment.BottomEnd,
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 4.dp,
            modifier = Modifier.padding(8.dp).size(36.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (subtitle.isNotBlank()) {
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun MediaCard(
  item: YTItem,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  width: Dp = 150.dp,
) {
  MediaCard(
    title = item.title,
    subtitle = item.cardSubtitle(),
    artworkUrl = item.thumbnail,
    onClick = onClick,
    modifier = modifier,
    width = width,
  )
}

fun YTItem.cardSubtitle(): String =
  when (this) {
    is SongItem -> artists.joinToString(", ") { it.name }
    is AlbumItem ->
      listOfNotNull(
          artists?.joinToString(", ") { it.name },
          year?.toString(),
        )
        .joinToString(" · ")
        .ifEmpty { "Album" }
    is PlaylistItem -> author?.name ?: songCountText ?: "Playlist"
    is ArtistItem -> "Artist"
    else -> ""
  }
