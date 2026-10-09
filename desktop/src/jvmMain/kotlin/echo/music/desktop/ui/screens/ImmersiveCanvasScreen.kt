package echo.music.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import echo.music.desktop.lyrics.DesktopLyricsResolver
import echo.music.desktop.ui.components.SyncedLyricsView
import echo.music.desktop.ui.theme.PaletteGenerator
import echo.music.desktop.ui.theme.meshGradientBrush
import echo.music.desktop.ui.theme.rememberAnimatedShaderTime
import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackState
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val HUD_HIDE_DELAY_MS = 3_000L

private const val MESH_INTENSITY = 1.05f

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImmersiveCanvasScreen(
  onExit: () -> Unit,
  lyricsResolver: DesktopLyricsResolver,
  modifier: Modifier = Modifier,
) {
  val item by PlaybackManager.currentItem.collectAsState()
  val playbackState by PlaybackManager.state.collectAsState()
  val position by PlaybackManager.position.collectAsState()
  val duration by PlaybackManager.duration.collectAsState()
  val lyrics by lyricsResolver.current.collectAsState()
  val lyricsLoading by lyricsResolver.loading.collectAsState()

  val shaderTime = rememberAnimatedShaderTime()

  var palette by remember { mutableStateOf(PaletteGenerator.defaultPalette) }
  LaunchedEffect(item?.artworkUrl) { palette = PaletteGenerator.paletteFor(item?.artworkUrl) }
  val animatedColors = rememberAnimatedPalette(palette)

  var lastMouseMoveNanos by remember { mutableLongStateOf(System.nanoTime()) }
  var hudVisible by remember { mutableStateOf(true) }
  LaunchedEffect(lastMouseMoveNanos) {
    hudVisible = true
    delay(HUD_HIDE_DELAY_MS)
    hudVisible = false
  }

  Box(
    modifier =
      modifier.fillMaxSize().onPointerEvent(PointerEventType.Move) {
        lastMouseMoveNanos = System.nanoTime()
      }
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize().drawBehind {
          drawRect(
            meshGradientBrush(
              colors = animatedColors,
              timeSeconds = shaderTime.floatValue,
              intensity = MESH_INTENSITY,
              widthPx = size.width,
              heightPx = size.height,
            )
          )
          drawRect(Color.Black.copy(alpha = 0.22f))
        }
    )
    Column(
      modifier = Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 28.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = "IMMERSIVE MODE",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
      )
      item?.let { current ->
        Text(
          text = current.title,
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        )
        if (current.artist.isNotBlank()) {
          Text(
            text = current.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
          )
        }
      }
    }
    SyncedLyricsView(
      result = lyrics,
      loading = lyricsLoading,
      compact = false,
      autoScroll = true,
      modifier = Modifier.align(Alignment.Center).fillMaxWidth(0.86f).padding(bottom = 72.dp),
    )
    AnimatedVisibility(
      visible = hudVisible,
      enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it },
      exit = fadeOut(tween(350)) + slideOutVertically(tween(350)) { it },
      modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
    ) {
      PlaybackHud(
        artworkUrl = item?.artworkUrl,
        title = item?.title ?: "Nothing playing",
        artist = item?.artist.orEmpty(),
        isPlaying = playbackState == PlaybackState.Playing,
        positionMs = position,
        durationMs = duration,
        onTogglePlay = { PlaybackManager.togglePlay() },
        onPrevious = { PlaybackManager.previous() },
        onNext = { PlaybackManager.next() },
        onClose = onExit,
      )
    }
  }
}

@Composable
private fun rememberAnimatedPalette(palette: List<Color>): List<Color> =
  List(6) { index ->
    val target = palette.getOrElse(index) { palette.lastOrNull() ?: Color.Black }
    animateColorAsState(targetValue = target, animationSpec = tween(800), label = "palette$index")
      .value
  }

@Composable
private fun PlaybackHud(
  artworkUrl: String?,
  title: String,
  artist: String,
  isPlaying: Boolean,
  positionMs: Long,
  durationMs: Long,
  onTogglePlay: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onClose: () -> Unit,
) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
    tonalElevation = 6.dp,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AsyncImage(
        model = artworkUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
          Modifier.size(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f)),
      )
      Column(modifier = Modifier.width(220.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          color = MaterialTheme.colorScheme.onSurface,
        )
        if (artist.isNotBlank()) {
          Text(
            text = artist,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(onClick = onPrevious) {
        Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
      }
      IconButton(onClick = onTogglePlay) {
        Icon(
          if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = if (isPlaying) "Pause" else "Play",
          modifier = Modifier.size(32.dp),
        )
      }
      IconButton(onClick = onNext) {
        Icon(Icons.Default.SkipNext, contentDescription = "Next")
      }
      Column(modifier = Modifier.width(280.dp)) {
        Slider(
          value = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
          onValueChange = { fraction ->
            if (durationMs > 0) PlaybackManager.seekTo((fraction * durationMs).toLong())
          },
          enabled = durationMs > 0,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(
            text = formatTimestamp(positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text = formatTimestamp(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      IconButton(onClick = onClose) {
        Icon(Icons.Default.Close, contentDescription = "Exit immersive mode")
      }
    }
  }
}

private fun formatTimestamp(millis: Long): String {
  if (millis <= 0L) return "0:00"
  val totalSeconds = millis.milliseconds.inWholeSeconds
  return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
