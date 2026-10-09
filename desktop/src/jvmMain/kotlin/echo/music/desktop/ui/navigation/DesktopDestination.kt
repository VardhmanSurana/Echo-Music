package echo.music.desktop.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

enum class DesktopDestination(val label: String) {
  HOME("Home"),
  EXPLORE("Explore & Charts"),
  LIBRARY("Library"),
  LIKED_SONGS("Liked Songs"),
  PLAYLISTS("Playlists"),
  ARTISTS("Artists"),
  LOCAL_LIBRARY("Local Library"),
  FOLDERS("Folders"),
  DOWNLOADED_OFFLINE("Downloaded Offline"),
  SETTINGS("Settings"),
  EQUALIZER("EQ & Audio"),
}

@Composable
fun PlaceholderDestination(destination: DesktopDestination, modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(text = destination.label, style = MaterialTheme.typography.headlineSmall)
  }
}
