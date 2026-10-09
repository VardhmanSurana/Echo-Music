package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.music.desktop.ui.shell.DesktopShellState
import echo.music.desktop.ui.shell.RightPanelTab
import echo.music.playback.PlaybackManager

@Composable
fun QueueLyricsPanel(
  state: DesktopShellState,
  modifier: Modifier = Modifier,
  lyricsContent: @Composable () -> Unit = {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Lyrics will appear here", style = MaterialTheme.typography.bodyLarge)
    }
  },
) {
  Column(
    modifier =
      modifier
        .width(320.dp)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      PanelTab(
        label = "Queue",
        selected = state.rightPanelTab == RightPanelTab.QUEUE,
        onClick = { state.rightPanelTab = RightPanelTab.QUEUE },
      )
      Spacer(modifier = Modifier.width(4.dp))
      PanelTab(
        label = "Lyrics",
        selected = state.rightPanelTab == RightPanelTab.LYRICS,
        onClick = { state.rightPanelTab = RightPanelTab.LYRICS },
      )
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = { state.rightPanelVisible = false }, modifier = Modifier.size(28.dp)) {
        Icon(
          Icons.Default.Close,
          contentDescription = "Close panel",
          modifier = Modifier.size(18.dp),
        )
      }
    }
    when (state.rightPanelTab) {
      RightPanelTab.QUEUE -> QueueTab()
      RightPanelTab.LYRICS -> lyricsContent()
    }
  }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
  TextButton(onClick = onClick) {
    Text(
      text = label,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
      color =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ColumnScope.QueueTab() {
  val queue by PlaybackManager.queue.collectAsState()
  val currentIndex by PlaybackManager.currentIndex.collectAsState()
  LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
    items(queue.size) { index ->
      val item = queue[index]
      val current = index == currentIndex
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
              if (current) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surface
            )
            .clickable { PlaybackManager.jumpTo(index) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (item.artist.isNotBlank()) {
            Text(
              text = item.artist,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        IconButton(
          enabled = index > 0,
          onClick = { PlaybackManager.move(index, index - 1) },
          modifier = Modifier.size(26.dp),
        ) {
          Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = "Move up",
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(
          enabled = index < queue.size - 1,
          onClick = { PlaybackManager.move(index, index + 1) },
          modifier = Modifier.size(26.dp),
        ) {
          Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Move down",
            modifier = Modifier.size(16.dp),
          )
        }
        IconButton(onClick = { PlaybackManager.removeAt(index) }, modifier = Modifier.size(26.dp)) {
          Icon(
            Icons.Default.Remove,
            contentDescription = "Remove",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
  Row(
    horizontalArrangement = Arrangement.End,
    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
  ) {
    TextButton(
      onClick = { PlaybackManager.queue.value.indices.forEach { PlaybackManager.removeAt(0) } },
      enabled = queue.isNotEmpty(),
    ) {
      Text("Clear")
    }
  }
}
