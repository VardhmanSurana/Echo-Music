package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
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
  width: Dp = 150.dp,
) {
  Column(modifier = modifier.width(width).clickable(onClick = onClick).padding(4.dp)) {
    AsyncImage(
      model = artworkUrl,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier =
        Modifier.fillMaxWidth()
          .aspectRatio(1f)
          .clip(RoundedCornerShape(8.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = title,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(
      text = subtitle,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
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
