package echo.music.desktop.ui.shell

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

sealed interface ShortcutAction {
  data object TogglePlay : ShortcutAction

  data object Next : ShortcutAction

  data object Previous : ShortcutAction

  data object ToggleShuffle : ShortcutAction

  data object CycleRepeat : ShortcutAction

  data object ToggleSidebar : ShortcutAction

  data object ToggleRightPanel : ShortcutAction

  data object ToggleMiniPlayer : ShortcutAction

  data object OpenSearch : ShortcutAction

  data object ToggleImmersive : ShortcutAction

  data object OpenSettings : ShortcutAction

  data object Quit : ShortcutAction
}

object KeyboardShortcuts {
  fun match(
    key: Key,
    ctrl: Boolean = false,
    shift: Boolean = false,
    alt: Boolean = false,
  ): ShortcutAction? {
    if (alt) return null
    if (!ctrl) {
      return when (key) {
        Key.Spacebar -> ShortcutAction.TogglePlay
        Key.F11 -> ShortcutAction.ToggleImmersive
        else -> null
      }
    }
    return when (key) {
      Key.DirectionRight -> ShortcutAction.Next
      Key.DirectionLeft -> ShortcutAction.Previous
      Key.S -> ShortcutAction.ToggleShuffle
      Key.R -> ShortcutAction.CycleRepeat
      Key.One -> ShortcutAction.ToggleSidebar
      Key.Two -> ShortcutAction.ToggleRightPanel
      Key.M -> ShortcutAction.ToggleMiniPlayer
      Key.K -> ShortcutAction.OpenSearch
      Key.Comma -> ShortcutAction.OpenSettings
      Key.Q -> ShortcutAction.Quit
      else -> null
    }
  }

  fun fromEvent(event: KeyEvent): ShortcutAction? {
    if (event.type != KeyEventType.KeyDown) return null
    return match(
      key = event.key,
      ctrl = event.isCtrlPressed,
      shift = event.isShiftPressed,
      alt = event.isAltPressed,
    )
  }
}
