package echo.music.desktop.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import echo.music.desktop.ui.navigation.DesktopDestination

internal fun iconFor(destination: DesktopDestination): ImageVector =
  when (destination) {
    DesktopDestination.HOME -> Icons.Default.Home
    DesktopDestination.EXPLORE -> Icons.Default.Explore
    DesktopDestination.LIBRARY -> Icons.Default.LibraryMusic
    DesktopDestination.LIKED_SONGS -> Icons.Default.Favorite
    DesktopDestination.PLAYLISTS -> Icons.Default.PlaylistPlay
    DesktopDestination.ARTISTS -> Icons.Default.Person
    DesktopDestination.LOCAL_LIBRARY -> Icons.Default.MusicNote
    DesktopDestination.FOLDERS -> Icons.Default.Folder
    DesktopDestination.DOWNLOADED_OFFLINE -> Icons.Default.Download
    DesktopDestination.SETTINGS -> Icons.Default.Settings
    DesktopDestination.EQUALIZER -> Icons.Default.GraphicEq
  }

private val onlineDestinations =
  listOf(
    DesktopDestination.HOME,
    DesktopDestination.EXPLORE,
    DesktopDestination.LIKED_SONGS,
    DesktopDestination.PLAYLISTS,
    DesktopDestination.ARTISTS,
  )

private val localDestinations =
  listOf(
    DesktopDestination.LOCAL_LIBRARY,
    DesktopDestination.FOLDERS,
    DesktopDestination.DOWNLOADED_OFFLINE,
  )

private val systemDestinations = listOf(DesktopDestination.SETTINGS, DesktopDestination.EQUALIZER)

private val sections =
  listOf(
    "Online" to onlineDestinations,
    "Local" to localDestinations,
    "System" to systemDestinations,
  )

@Composable
fun Sidebar(
  selected: DesktopDestination,
  onDestinationSelected: (DesktopDestination) -> Unit,
  expanded: Boolean,
  modifier: Modifier = Modifier,
) {
  val width: Dp by animateDpAsState(if (expanded) 220.dp else 64.dp, label = "sidebarWidth")
  Column(
    modifier =
      modifier
        .width(width)
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.surfaceContainerLow)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 8.dp, vertical = 12.dp)
  ) {
    sections.forEach { (title, destinations) ->
      if (expanded) {
        Text(
          text = title,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
        )
      } else {
        Spacer(modifier = Modifier.height(12.dp))
      }
      destinations.forEach { destination ->
        SidebarRow(
          destination = destination,
          selected = destination == selected,
          expanded = expanded,
          onClick = { onDestinationSelected(destination) },
        )
      }
    }
  }
}

@Composable
private fun SidebarRow(
  destination: DesktopDestination,
  selected: Boolean,
  expanded: Boolean,
  onClick: () -> Unit,
) {
  val background =
    if (selected) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surfaceContainerLow
  val contentColor =
    if (selected) MaterialTheme.colorScheme.onSecondaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .height(40.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(background)
        .clickable(onClick = onClick)
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = iconFor(destination),
      contentDescription = destination.label,
      tint = contentColor,
      modifier = Modifier.size(20.dp),
    )
    if (expanded) {
      Spacer(modifier = Modifier.width(12.dp))
      Text(
        text = destination.label,
        style = MaterialTheme.typography.bodyMedium,
        color =
          if (selected) MaterialTheme.colorScheme.onSecondaryContainer
          else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
      )
    }
  }
}
