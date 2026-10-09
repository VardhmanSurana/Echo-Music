package echo.music.desktop.ui.shell

import androidx.compose.ui.input.key.Key
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyboardShortcutsTest {

  @Test
  fun spaceMapsToTogglePlay() {
    assertEquals(ShortcutAction.TogglePlay, KeyboardShortcuts.match(Key.Spacebar))
  }

  @Test
  fun f11MapsToToggleImmersive() {
    assertEquals(ShortcutAction.ToggleImmersive, KeyboardShortcuts.match(Key.F11))
  }

  @Test
  fun ctrlArrowKeysMapToNavigation() {
    assertEquals(ShortcutAction.Next, KeyboardShortcuts.match(Key.DirectionRight, ctrl = true))
    assertEquals(ShortcutAction.Previous, KeyboardShortcuts.match(Key.DirectionLeft, ctrl = true))
  }

  @Test
  fun ctrlLetterCombosMapToActions() {
    assertEquals(ShortcutAction.ToggleShuffle, KeyboardShortcuts.match(Key.S, ctrl = true))
    assertEquals(ShortcutAction.CycleRepeat, KeyboardShortcuts.match(Key.R, ctrl = true))
    assertEquals(ShortcutAction.ToggleSidebar, KeyboardShortcuts.match(Key.One, ctrl = true))
    assertEquals(ShortcutAction.ToggleRightPanel, KeyboardShortcuts.match(Key.Two, ctrl = true))
    assertEquals(ShortcutAction.ToggleMiniPlayer, KeyboardShortcuts.match(Key.M, ctrl = true))
    assertEquals(ShortcutAction.OpenSearch, KeyboardShortcuts.match(Key.K, ctrl = true))
    assertEquals(ShortcutAction.OpenSettings, KeyboardShortcuts.match(Key.Comma, ctrl = true))
    assertEquals(ShortcutAction.Quit, KeyboardShortcuts.match(Key.Q, ctrl = true))
  }

  @Test
  fun plainLettersAreUnmapped() {
    assertNull(KeyboardShortcuts.match(Key.S))
    assertNull(KeyboardShortcuts.match(Key.K))
    assertNull(KeyboardShortcuts.match(Key.Q))
  }

  @Test
  fun unmappedCtrlCombosReturnNull() {
    assertNull(KeyboardShortcuts.match(Key.Spacebar, ctrl = true))
    assertNull(KeyboardShortcuts.match(Key.DirectionUp, ctrl = true))
    assertNull(KeyboardShortcuts.match(Key.F11, ctrl = true))
  }

  @Test
  fun altCombinationsAreUnmapped() {
    assertNull(KeyboardShortcuts.match(Key.S, ctrl = true, alt = true))
    assertNull(KeyboardShortcuts.match(Key.Spacebar, alt = true))
  }

  @Test
  fun shiftDoesNotAffectMapping() {
    assertEquals(
      ShortcutAction.ToggleShuffle,
      KeyboardShortcuts.match(Key.S, ctrl = true, shift = true),
    )
    assertEquals(
      ShortcutAction.Next,
      KeyboardShortcuts.match(Key.DirectionRight, ctrl = true, shift = true),
    )
  }
}
