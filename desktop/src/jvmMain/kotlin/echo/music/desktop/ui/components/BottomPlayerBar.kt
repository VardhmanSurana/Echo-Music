package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.music.desktop.ui.shell.DesktopShellState
import echo.music.desktop.ui.shell.RightPanelTab
import echo.music.desktop.ui.shell.next
import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackState
import echo.music.playback.RepeatMode

internal fun formatTime(ms: Long): String {
  if (ms <= 0L) return "0:00"
  val totalSeconds = ms / 1000
  val seconds = totalSeconds % 60
  val minutes = (totalSeconds / 60) % 60
  val hours = totalSeconds / 3600
  return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
  else "%d:%02d".format(minutes, seconds)
}

@Composable
fun BottomPlayerBar(
  state: DesktopShellState,
  onEnterImmersive: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val item by PlaybackManager.currentItem.collectAsState()
  val playbackState by PlaybackManager.state.collectAsState()
  val position by PlaybackManager.position.collectAsState()
  val duration by PlaybackManager.duration.collectAsState()
  val volume by PlaybackManager.volume.collectAsState()

  var liked by remember(item?.id) { mutableStateOf(false) }
  var lastVolume by remember { mutableStateOf(1f) }
  var outputMenuExpanded by remember { mutableStateOf(false) }

  Surface(modifier = modifier.fillMaxWidth().height(72.dp), tonalElevation = 3.dp) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ArtworkPlaceholder(title = item?.title ?: "")
      Spacer(modifier = Modifier.width(10.dp))
      Column(modifier = Modifier.width(170.dp)) {
        Text(
          text = item?.title ?: "Nothing playing",
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        val current = item
        val subtitle =
          when {
            current == null -> ""
            current.artist.isBlank() -> current.album
            else -> "${current.artist} — ${current.album}".trimEnd(' ', '—')
          }
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = { liked = !liked }) {
        Icon(
          imageVector = if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          contentDescription = "Like",
          tint =
            if (liked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.weight(1f))
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              state.shuffleEnabled = !state.shuffleEnabled
              PlaybackManager.setShuffle(state.shuffleEnabled)
            }
          ) {
            Icon(
              Icons.Default.Shuffle,
              contentDescription = "Shuffle",
              tint =
                if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { PlaybackManager.previous() }) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
          }
          FilledIconButton(
            onClick = { PlaybackManager.togglePlay() },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
          ) {
            when (playbackState) {
              PlaybackState.Loading,
              is PlaybackState.Buffering ->
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
              else ->
                Icon(
                  imageVector =
                    if (playbackState == PlaybackState.Playing) Icons.Default.Pause
                    else Icons.Default.PlayArrow,
                  contentDescription =
                    if (playbackState == PlaybackState.Playing) "Pause" else "Play",
                  modifier = Modifier.size(24.dp),
                )
            }
          }
          IconButton(onClick = { PlaybackManager.next() }) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next")
          }
          IconButton(
            onClick = {
              state.repeatMode = state.repeatMode.next()
              PlaybackManager.setRepeatMode(state.repeatMode)
            }
          ) {
            Icon(
              imageVector =
                if (state.repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne
                else Icons.Default.Repeat,
              contentDescription = "Repeat",
              tint =
                if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        ProgressRow(position = position, duration = duration, enabled = item != null)
      }
      Spacer(modifier = Modifier.weight(1f))
      IconButton(
        onClick = {
          if (volume > 0f) {
            lastVolume = volume
            PlaybackManager.setVolume(0f)
          } else {
            PlaybackManager.setVolume(if (lastVolume > 0f) lastVolume else 1f)
          }
        }
      ) {
        Icon(
          imageVector =
            when {
              volume <= 0f -> Icons.Default.VolumeOff
              volume < 0.5f -> Icons.Default.VolumeDown
              else -> Icons.Default.VolumeUp
            },
          contentDescription = "Mute",
        )
      }
      Slider(
        value = volume,
        onValueChange = { PlaybackManager.setVolume(it) },
        modifier = Modifier.width(100.dp),
      )
      Box {
        IconButton(onClick = { outputMenuExpanded = true }) {
          Icon(Icons.Default.Speaker, contentDescription = "Output device")
        }
        DropdownMenu(
          expanded = outputMenuExpanded,
          onDismissRequest = { outputMenuExpanded = false },
        ) {
          DropdownMenuItem(
            text = { Text("System Default") },
            onClick = { outputMenuExpanded = false },
          )
        }
      }
      IconButton(
        onClick = {
          state.rightPanelVisible = true
          state.rightPanelTab = RightPanelTab.LYRICS
        }
      ) {
        Icon(Icons.Default.Lyrics, contentDescription = "Lyrics")
      }
      IconButton(
        onClick = {
          state.rightPanelVisible = true
          state.rightPanelTab = RightPanelTab.QUEUE
        }
      ) {
        Icon(Icons.Default.QueueMusic, contentDescription = "Queue")
      }
      IconButton(onClick = onEnterImmersive) {
        Icon(Icons.Default.Fullscreen, contentDescription = "Immersive mode")
      }
      val engine by PlaybackManager.activeEngine.collectAsState()
      Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
          Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(engineLabel(engine), style = MaterialTheme.typography.labelSmall)
        }
      }
    }
  }
}

@Composable
private fun ArtworkPlaceholder(title: String) {
  Box(
    modifier =
      Modifier.size(48.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.secondaryContainer),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = title.firstOrNull()?.uppercase() ?: "♪",
      style = MaterialTheme.typography.titleLarge,
      color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
  }
}

@Composable
private fun ProgressRow(position: Long, duration: Long, enabled: Boolean) {
  var seekValue by remember { mutableStateOf<Float?>(null) }
  val sliderValue = seekValue ?: position.toFloat()
  val fraction = if (duration > 0) (sliderValue / duration).coerceIn(0f, 1f) else 0f
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.width(420.dp),
  ) {
    Text(
      text = formatTime(sliderValue.toLong()),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.width(44.dp),
    )
    Slider(
      value = fraction,
      onValueChange = { fractionValue -> seekValue = fractionValue * duration },
      onValueChangeFinished = {
        seekValue?.let { PlaybackManager.seekTo(it.toLong()) }
        seekValue = null
      },
      enabled = enabled && duration > 0,
      modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
    )
    Text(
      text = formatTime(duration),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.width(44.dp),
    )
  }
}
