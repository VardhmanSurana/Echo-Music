package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import echo.music.desktop.auth.AuthSyncState
import echo.music.desktop.local.DesktopPreferences
import echo.music.desktop.local.LocalMediaScanner
import echo.music.desktop.ui.components.CookieInputDialog
import echo.music.desktop.ui.theme.ThemeMode
import echo.music.desktop.ui.theme.ThemeSettings
import echo.music.playback.EngineType
import echo.music.playback.PlaybackManager
import java.text.DateFormat
import java.util.Date

@Composable
fun SettingsScreen(
  preferences: DesktopPreferences,
  scanner: LocalMediaScanner,
  modifier: Modifier = Modifier,
) {
  var cookieDialogVisible by remember { mutableStateOf(false) }
  if (cookieDialogVisible) {
    CookieInputDialog(onDismiss = { cookieDialogVisible = false })
  }
  Column(
    modifier =
      modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).width(720.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(text = "Settings", style = MaterialTheme.typography.headlineSmall)
    AppearanceSection(preferences)
    HorizontalDivider()
    AudioSection(preferences)
    HorizontalDivider()
    LocalLibrarySection(scanner)
    HorizontalDivider()
    AccountSyncSection(onManualCookieInput = { cookieDialogVisible = true })
    HorizontalDivider()
    AboutSection()
  }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(text = title, style = MaterialTheme.typography.titleSmall)
      Spacer(modifier = Modifier.height(8.dp))
      content()
    }
  }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onSelect: () -> Unit) {
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .selectable(selected = selected, onClick = onSelect)
        .padding(vertical = 2.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected = selected, onClick = null)
    Spacer(modifier = Modifier.width(8.dp))
    Text(text = label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun AppearanceSection(preferences: DesktopPreferences) {
  val mode by ThemeSettings.mode.collectAsState()
  SettingsCard("Appearance") {
    ThemeMode.entries.forEach { themeMode ->
      RadioRow(
        label =
          when (themeMode) {
            ThemeMode.SYSTEM -> "System"
            ThemeMode.LIGHT -> "Light"
            ThemeMode.DARK -> "Dark"
          },
        selected = mode == themeMode,
        onSelect = {
          ThemeSettings.setMode(themeMode)
          preferences.themeMode = themeMode.name
        },
      )
    }
  }
}

@Composable
private fun AudioSection(preferences: DesktopPreferences) {
  val activeEngine by PlaybackManager.activeEngine.collectAsState()
  val volume by PlaybackManager.volume.collectAsState()
  SettingsCard("Audio") {
    RadioRow(
      label = "Auto (libmpv, then GStreamer, then fallback)",
      selected = activeEngine == EngineType.AUTO,
      onSelect = {
        PlaybackManager.setEngine(EngineType.AUTO)
        preferences.engineType = EngineType.AUTO.name
      },
    )
    RadioRow(
      label = "GStreamer",
      selected = activeEngine == EngineType.GSTREAMER,
      onSelect = {
        PlaybackManager.setEngine(EngineType.GSTREAMER)
        preferences.engineType = EngineType.GSTREAMER.name
      },
    )
    RadioRow(
      label = "Fallback (built-in)",
      selected = activeEngine == EngineType.FALLBACK,
      onSelect = {
        PlaybackManager.setEngine(EngineType.FALLBACK)
        preferences.engineType = EngineType.FALLBACK.name
      },
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text =
        "Active engine: $activeEngine. If libmpv or GStreamer are missing the next " +
          "available engine is used automatically.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = "Default volume: ${(volume * 100).toInt()}% (adjust with the player slider).",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun LocalLibrarySection(scanner: LocalMediaScanner) {
  val roots by scanner.roots.collectAsState()
  val isScanning by scanner.isScanning.collectAsState()
  SettingsCard("Local Library") {
    if (roots.isEmpty()) {
      Text(
        text = "No folders watched yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    roots.forEach { root ->
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = root,
          style = MaterialTheme.typography.bodySmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { scanner.removeRoot(root) }) {
          Icon(Icons.Default.Delete, contentDescription = "Remove folder")
        }
      }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Row {
      TextButton(onClick = { chooseDirectory()?.let { scanner.addRoot(it) } }) {
        Icon(Icons.Default.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add folder")
      }
      Spacer(modifier = Modifier.width(8.dp))
      TextButton(onClick = { scanner.rescan() }, enabled = !isScanning) { Text("Rescan now") }
    }
  }
}

@Composable
private fun AccountSyncSection(onManualCookieInput: () -> Unit) {
  val status by AuthSyncState.status.collectAsState()
  var instructionsVisible by remember { mutableStateOf(false) }
  SettingsCard("Account & Sync") {
    Text(
      text =
        if (status.synced) {
          val syncedAt =
            status.lastSyncEpochMs?.let {
              DateFormat.getDateTimeInstance().format(Date(it))
            } ?: "unknown time"
          "Extension sync: synced ($syncedAt) · cookies: " +
            status.cookieNames.joinToString(", ").ifEmpty { "none listed" }
        } else {
          "Extension sync: not synced yet."
        },
      style = MaterialTheme.typography.bodySmall,
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row {
      TextButton(onClick = { instructionsVisible = !instructionsVisible }) {
        Icon(Icons.Default.OpenInNew, contentDescription = null)
        Spacer(modifier = Modifier.width(4.dp))
        Text("Open extension setup instructions")
      }
    }
    if (instructionsVisible) {
      Text(
        text =
          "1. Open chrome://extensions (or the extensions page of your browser).\n" +
            "2. Enable Developer mode.\n" +
            "3. Click \"Load unpacked\" and select the companion-extension directory from the " +
            "Echo Music repository.\n" +
            "4. Keep the extension enabled; it syncs your youtube.com cookies to the desktop " +
            "app automatically.\n" +
            "Alternatively use Manual Cookie Input from the Account & Sync menu.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row { TextButton(onClick = onManualCookieInput) { Text("Manual Cookie Input…") } }
  }
}

@Composable
private fun AboutSection() {
  SettingsCard("About") {
    Text(text = "Echo Music 1.0.0-dev", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text =
        "Open source desktop client for YouTube Music and local files. " +
          "Released under the GPL-3.0 license.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
