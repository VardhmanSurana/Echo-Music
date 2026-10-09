package echo.music.desktop.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import echo.music.desktop.ui.navigation.DesktopDestination
import echo.music.playback.PlaybackManager
import echo.music.playback.RepeatMode

enum class RightPanelTab {
  QUEUE,
  LYRICS,
}

class DesktopShellState {
  var selectedDestination by mutableStateOf(DesktopDestination.HOME)
  var sidebarExpanded by mutableStateOf(true)
  var rightPanelVisible by mutableStateOf(false)
  var rightPanelTab by mutableStateOf(RightPanelTab.QUEUE)
  var miniPlayerVisible by mutableStateOf(false)
  var searchOpen by mutableStateOf(false)
  var shuffleEnabled by mutableStateOf(false)
  var repeatMode by mutableStateOf(RepeatMode.OFF)
}

fun RepeatMode.next(): RepeatMode =
  when (this) {
    RepeatMode.OFF -> RepeatMode.ONE
    RepeatMode.ONE -> RepeatMode.ALL
    RepeatMode.ALL -> RepeatMode.OFF
  }

fun reduce(state: DesktopShellState, action: ShortcutAction) {
  when (action) {
    ShortcutAction.TogglePlay -> PlaybackManager.togglePlay()
    ShortcutAction.Next -> PlaybackManager.next()
    ShortcutAction.Previous -> PlaybackManager.previous()
    ShortcutAction.ToggleShuffle -> {
      state.shuffleEnabled = !state.shuffleEnabled
      PlaybackManager.setShuffle(state.shuffleEnabled)
    }
    ShortcutAction.CycleRepeat -> {
      state.repeatMode = state.repeatMode.next()
      PlaybackManager.setRepeatMode(state.repeatMode)
    }
    ShortcutAction.ToggleSidebar -> state.sidebarExpanded = !state.sidebarExpanded
    ShortcutAction.ToggleRightPanel -> state.rightPanelVisible = !state.rightPanelVisible
    ShortcutAction.ToggleMiniPlayer -> state.miniPlayerVisible = !state.miniPlayerVisible
    ShortcutAction.OpenSearch -> state.searchOpen = true
    ShortcutAction.ToggleImmersive -> Unit
    ShortcutAction.OpenSettings -> state.selectedDestination = DesktopDestination.SETTINGS
    ShortcutAction.Quit -> Unit
  }
}

fun dispatchShortcut(
  state: DesktopShellState,
  action: ShortcutAction,
  onQuit: () -> Unit,
  onEnterImmersive: () -> Unit,
) {
  when (action) {
    ShortcutAction.Quit -> onQuit()
    ShortcutAction.ToggleImmersive -> onEnterImmersive()
    else -> reduce(state, action)
  }
}
