package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackState

@Composable
fun MiniPlayer(onClose: () -> Unit, modifier: Modifier = Modifier) {
  val item by PlaybackManager.currentItem.collectAsState()
  val playbackState by PlaybackManager.state.collectAsState()
  Surface(
    modifier = modifier.fillMaxSize(),
    color = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 4.dp,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
    ) {
      MiniPlayerArtwork(title = item?.title ?: "")
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item?.title ?: "Nothing playing",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = item?.artist.orEmpty(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = { PlaybackManager.togglePlay() }) {
        Icon(
          imageVector =
            if (playbackState == PlaybackState.Playing) Icons.Default.Pause
            else Icons.Default.PlayArrow,
          contentDescription = if (playbackState == PlaybackState.Playing) "Pause" else "Play",
        )
      }
      IconButton(onClick = { PlaybackManager.next() }) {
        Icon(Icons.Default.SkipNext, contentDescription = "Next")
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Close mini player")
      }
    }
  }
}

@Composable
private fun MiniPlayerArtwork(title: String) {
  Box(
    modifier =
      Modifier.size(56.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = title.firstOrNull()?.uppercase() ?: "♪",
      style = MaterialTheme.typography.headlineSmall,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}
