package echo.music.desktop.ui.shell

import echo.music.desktop.ui.navigation.DesktopDestination
import echo.music.playback.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopShellStateTest {

  @Test
  fun toggleActionsFlipFlags() {
    val state = DesktopShellState()
    assertTrue(state.sidebarExpanded)
    reduce(state, ShortcutAction.ToggleSidebar)
    assertFalse(state.sidebarExpanded)
    reduce(state, ShortcutAction.ToggleSidebar)
    assertTrue(state.sidebarExpanded)

    assertFalse(state.rightPanelVisible)
    reduce(state, ShortcutAction.ToggleRightPanel)
    assertTrue(state.rightPanelVisible)
    reduce(state, ShortcutAction.ToggleRightPanel)
    assertFalse(state.rightPanelVisible)

    assertFalse(state.miniPlayerVisible)
    reduce(state, ShortcutAction.ToggleMiniPlayer)
    assertTrue(state.miniPlayerVisible)
    reduce(state, ShortcutAction.ToggleMiniPlayer)
    assertFalse(state.miniPlayerVisible)
  }

  @Test
  fun openSearchSetsFlag() {
    val state = DesktopShellState()
    assertFalse(state.searchOpen)
    reduce(state, ShortcutAction.OpenSearch)
    assertTrue(state.searchOpen)
  }

  @Test
  fun openSettingsSelectsSettingsDestination() {
    val state = DesktopShellState()
    assertEquals(DesktopDestination.HOME, state.selectedDestination)
    reduce(state, ShortcutAction.OpenSettings)
    assertEquals(DesktopDestination.SETTINGS, state.selectedDestination)
  }

  @Test
  fun rightPanelTabCanBeSwitched() {
    val state = DesktopShellState()
    assertEquals(RightPanelTab.QUEUE, state.rightPanelTab)
    state.rightPanelTab = RightPanelTab.LYRICS
    assertEquals(RightPanelTab.LYRICS, state.rightPanelTab)
  }

  @Test
  fun repeatCyclesOffOneAll() {
    val state = DesktopShellState()
    assertEquals(RepeatMode.OFF, state.repeatMode)
    reduce(state, ShortcutAction.CycleRepeat)
    assertEquals(RepeatMode.ONE, state.repeatMode)
    reduce(state, ShortcutAction.CycleRepeat)
    assertEquals(RepeatMode.ALL, state.repeatMode)
    reduce(state, ShortcutAction.CycleRepeat)
    assertEquals(RepeatMode.OFF, state.repeatMode)
  }

  @Test
  fun shuffleToggles() {
    val state = DesktopShellState()
    assertFalse(state.shuffleEnabled)
    reduce(state, ShortcutAction.ToggleShuffle)
    assertTrue(state.shuffleEnabled)
    reduce(state, ShortcutAction.ToggleShuffle)
    assertFalse(state.shuffleEnabled)
  }

  @Test
  fun repeatModeNextWraps() {
    assertEquals(RepeatMode.ONE, RepeatMode.OFF.next())
    assertEquals(RepeatMode.ALL, RepeatMode.ONE.next())
    assertEquals(RepeatMode.OFF, RepeatMode.ALL.next())
  }

  @Test
  fun dispatchRoutesQuitAndImmersiveToCallbacks() {
    val state = DesktopShellState()
    var quitCalled = false
    var immersiveCalled = false
    dispatchShortcut(
      state,
      ShortcutAction.Quit,
      onQuit = { quitCalled = true },
      onEnterImmersive = {},
    )
    dispatchShortcut(
      state,
      ShortcutAction.ToggleImmersive,
      onQuit = {},
      onEnterImmersive = { immersiveCalled = true },
    )
    assertTrue(quitCalled)
    assertTrue(immersiveCalled)
  }

  @Test
  fun dispatchRoutesRegularActionsThroughReducer() {
    val state = DesktopShellState()
    dispatchShortcut(state, ShortcutAction.ToggleSidebar, onQuit = {}, onEnterImmersive = {})
    dispatchShortcut(state, ShortcutAction.OpenSearch, onQuit = {}, onEnterImmersive = {})
    assertFalse(state.sidebarExpanded)
    assertTrue(state.searchOpen)
  }
}
