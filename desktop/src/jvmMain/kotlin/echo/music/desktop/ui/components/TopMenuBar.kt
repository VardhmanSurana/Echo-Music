package echo.music.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import echo.music.desktop.auth.AuthSyncState
import echo.music.desktop.ui.shell.DesktopShellState
import echo.music.desktop.ui.shell.LocalDesktopWindowState
import echo.music.desktop.ui.shell.ShortcutAction
import echo.music.desktop.ui.shell.dispatchShortcut
import echo.music.playback.EngineType
import echo.music.playback.PlaybackManager
import echo.music.playback.RepeatMode

internal fun engineLabel(type: EngineType): String =
  when (type) {
    EngineType.AUTO -> "Auto"
    EngineType.MPV -> "mpv"
    EngineType.GSTREAMER -> "GStreamer"
    EngineType.FALLBACK -> "Fallback"
  }

internal fun RepeatMode.label(): String =
  when (this) {
    RepeatMode.OFF -> "Off"
    RepeatMode.ONE -> "One"
    RepeatMode.ALL -> "All"
  }

@Composable
fun TopMenuBar(
  state: DesktopShellState,
  onOpenFile: () -> Unit,
  onOpenFolder: () -> Unit,
  onRescanLocalFolders: () -> Unit,
  onExportLibrary: () -> Unit,
  onQuit: () -> Unit,
  onEnterImmersive: () -> Unit,
  onCloseRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var cookieDialogVisible by remember { mutableStateOf(false) }
  if (cookieDialogVisible) {
    CookieInputDialog(onDismiss = { cookieDialogVisible = false })
  }
  Row(
    modifier =
      modifier
        .fillMaxHeight()
        .background(MaterialTheme.colorScheme.surface)
        .padding(horizontal = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = "Echo Music",
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(end = 8.dp),
    )
    FileMenu(state, onOpenFile, onOpenFolder, onRescanLocalFolders, onExportLibrary, onQuit)
    PlaybackMenu(state)
    AudioMenu()
    ViewMenu(state, onEnterImmersive)
    AccountMenu(onManualCookieInput = { cookieDialogVisible = true })
    Spacer(modifier = Modifier.weight(1f))
    QuickSearchBar(onClick = { state.searchOpen = true })
    Spacer(modifier = Modifier.width(8.dp))
    EngineChip()
    Spacer(modifier = Modifier.width(8.dp))
    SyncStatusDot()
    Spacer(modifier = Modifier.width(8.dp))
    WindowControls(onCloseRequest = onCloseRequest)
  }
}

@Composable
private fun MenuButton(
  label: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  Box {
    TextButton(
      onClick = { onExpandedChange(true) },
      contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
      modifier = Modifier.height(30.dp),
    ) {
      Text(label, style = MaterialTheme.typography.labelLarge)
    }
    DropdownMenu(
      expanded = expanded,
      onDismissRequest = { onExpandedChange(false) },
      content = content,
    )
  }
}

@Composable
private fun Hint(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.outline,
  )
}

@Composable
private fun FileMenu(
  state: DesktopShellState,
  onOpenFile: () -> Unit,
  onOpenFolder: () -> Unit,
  onRescanLocalFolders: () -> Unit,
  onExportLibrary: () -> Unit,
  onQuit: () -> Unit,
) {
  var expanded by remember { mutableStateOf(false) }
  MenuButton("File", expanded, { expanded = it }) {
    DropdownMenuItem(
      text = { Text("Open File…") },
      onClick = {
        expanded = false
        onOpenFile()
      },
    )
    DropdownMenuItem(
      text = { Text("Open Folder…") },
      onClick = {
        expanded = false
        onOpenFolder()
      },
    )
    DropdownMenuItem(
      text = { Text("Rescan Local Folders") },
      onClick = {
        expanded = false
        onRescanLocalFolders()
      },
    )
    DropdownMenuItem(
      text = { Text("Export Library") },
      onClick = {
        expanded = false
        onExportLibrary()
      },
    )
    DropdownMenuItem(
      text = { Text("Preferences") },
      trailingIcon = { Hint("Ctrl+,") },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.OpenSettings, {}, {})
      },
    )
    DropdownMenuItem(
      text = { Text("Quit") },
      trailingIcon = { Hint("Ctrl+Q") },
      onClick = {
        expanded = false
        onQuit()
      },
    )
  }
}

@Composable
private fun PlaybackMenu(state: DesktopShellState) {
  var expanded by remember { mutableStateOf(false) }
  MenuButton("Playback", expanded, { expanded = it }) {
    DropdownMenuItem(
      text = { Text("Play/Pause") },
      trailingIcon = { Hint("Space") },
      onClick = {
        expanded = false
        PlaybackManager.togglePlay()
      },
    )
    DropdownMenuItem(
      text = { Text("Next") },
      trailingIcon = { Hint("Ctrl+→") },
      onClick = {
        expanded = false
        PlaybackManager.next()
      },
    )
    DropdownMenuItem(
      text = { Text("Previous") },
      trailingIcon = { Hint("Ctrl+←") },
      onClick = {
        expanded = false
        PlaybackManager.previous()
      },
    )
    DropdownMenuItem(
      text = { Text("Shuffle") },
      trailingIcon = {
        if (state.shuffleEnabled) {
          Icon(
            Icons.Default.Check,
            contentDescription = "Shuffle on",
            modifier = Modifier.size(16.dp),
          )
        }
      },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.ToggleShuffle, {}, {})
      },
    )
    DropdownMenuItem(
      text = { Text("Repeat — ${state.repeatMode.label()}") },
      trailingIcon = { Hint("Ctrl+R") },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.CycleRepeat, {}, {})
      },
    )
  }
}

@Composable
private fun AudioMenu() {
  var expanded by remember { mutableStateOf(false) }
  var sinkExpanded by remember { mutableStateOf(false) }
  val engine by PlaybackManager.activeEngine.collectAsState()
  MenuButton("Audio", expanded, { expanded = it }) {
    EngineRadioItem("Auto (libmpv)", engine == EngineType.AUTO) {
      PlaybackManager.setEngine(EngineType.AUTO)
    }
    EngineRadioItem("GStreamer", engine == EngineType.GSTREAMER) {
      PlaybackManager.setEngine(EngineType.GSTREAMER)
    }
    EngineRadioItem("Fallback", engine == EngineType.FALLBACK) {
      PlaybackManager.setEngine(EngineType.FALLBACK)
    }
    Box {
      DropdownMenuItem(
        text = { Text("Output Sink") },
        trailingIcon = {
          Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
        },
        onClick = { sinkExpanded = true },
      )
      DropdownMenu(
        expanded = sinkExpanded,
        onDismissRequest = { sinkExpanded = false },
        offset = DpOffset(x = 160.dp, y = 0.dp),
      ) {
        DropdownMenuItem(text = { Text("System Default") }, onClick = { sinkExpanded = false })
      }
    }
    DropdownMenuItem(text = { Text("Equalizer…") }, onClick = { expanded = false })
  }
}

@Composable
private fun EngineRadioItem(label: String, selected: Boolean, onSelect: () -> Unit) {
  DropdownMenuItem(
    text = { Text(label) },
    leadingIcon = {
      RadioButton(selected = selected, onClick = null, modifier = Modifier.size(18.dp))
    },
    onClick = onSelect,
  )
}

@Composable
private fun ViewMenu(state: DesktopShellState, onEnterImmersive: () -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  MenuButton("View", expanded, { expanded = it }) {
    DropdownMenuItem(
      text = { Text(if (state.sidebarExpanded) "Hide Left Sidebar" else "Show Left Sidebar") },
      trailingIcon = { Hint("Ctrl+1") },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.ToggleSidebar, {}, {})
      },
    )
    DropdownMenuItem(
      text = { Text(if (state.rightPanelVisible) "Hide Right Panel" else "Show Right Panel") },
      trailingIcon = { Hint("Ctrl+2") },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.ToggleRightPanel, {}, {})
      },
    )
    DropdownMenuItem(
      text = { Text("Mini-Player") },
      trailingIcon = { Hint("Ctrl+M") },
      onClick = {
        expanded = false
        dispatchShortcut(state, ShortcutAction.ToggleMiniPlayer, {}, {})
      },
    )
    DropdownMenuItem(
      text = { Text("Fullscreen Immersive Mode") },
      trailingIcon = { Hint("F11") },
      onClick = {
        expanded = false
        onEnterImmersive()
      },
    )
  }
}

@Composable
private fun AccountMenu(onManualCookieInput: () -> Unit) {
  var expanded by remember { mutableStateOf(false) }
  val status = AuthSyncState.status.collectAsState().value
  MenuButton("Account & Sync", expanded, { expanded = it }) {
    DropdownMenuItem(
      text = {
        Text(if (status.synced) "Extension Sync: Synced" else "Extension Sync: Not synced")
      },
      enabled = false,
      onClick = { expanded = false },
    )
    DropdownMenuItem(
      text = { Text("Manual Cookie Input…") },
      onClick = {
        expanded = false
        onManualCookieInput()
      },
    )
    DropdownMenuItem(text = { Text("Sync Spotify Playlists…") }, enabled = false, onClick = {})
  }
}

@Composable
private fun QuickSearchBar(onClick: () -> Unit) {
  Row(
    modifier =
      Modifier.width(220.dp)
        .height(28.dp)
        .clip(RoundedCornerShape(50))
        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        .clickable(onClick = onClick)
        .padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      Icons.Default.Search,
      contentDescription = "Search",
      modifier = Modifier.size(14.dp),
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.width(6.dp))
    Text(
      "Search…",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.weight(1f))
    Text(
      "Ctrl+K",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.outline,
    )
  }
}

@Composable
private fun EngineChip() {
  val engine by PlaybackManager.activeEngine.collectAsState()
  Surface(
    shape = RoundedCornerShape(50),
    color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
  ) {
    Text(
      engineLabel(engine),
      style = MaterialTheme.typography.labelSmall,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    )
  }
}

@Composable
private fun SyncStatusDot() {
  val synced = AuthSyncState.status.collectAsState().value.synced
  Box(
    modifier =
      Modifier.size(10.dp)
        .clip(CircleShape)
        .background(if (synced) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline)
  )
}

@Composable
private fun WindowControls(onCloseRequest: () -> Unit) {
  val windowState: WindowState? = LocalDesktopWindowState.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    IconButton(
      onClick = { windowState?.isMinimized = true },
      enabled = windowState != null,
      modifier = Modifier.size(28.dp),
    ) {
      Icon(Icons.Default.Minimize, contentDescription = "Minimize", modifier = Modifier.size(14.dp))
    }
    IconButton(
      onClick = {
        if (windowState != null) {
          windowState.placement =
            if (windowState.placement == WindowPlacement.Maximized) WindowPlacement.Floating
            else WindowPlacement.Maximized
        }
      },
      enabled = windowState != null,
      modifier = Modifier.size(28.dp),
    ) {
      Icon(
        Icons.Default.CropSquare,
        contentDescription = "Maximize",
        modifier = Modifier.size(12.dp),
      )
    }
    IconButton(
      onClick = onCloseRequest,
      colors =
        IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
      modifier = Modifier.size(28.dp),
    ) {
      Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(14.dp))
    }
  }
}
