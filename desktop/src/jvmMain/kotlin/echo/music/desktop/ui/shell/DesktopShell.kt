package echo.music.desktop.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import echo.music.desktop.ui.components.BottomPlayerBar
import echo.music.desktop.ui.components.QueueLyricsPanel
import echo.music.desktop.ui.components.Sidebar
import echo.music.desktop.ui.components.TopMenuBar
import echo.music.desktop.ui.navigation.DesktopDestination
import echo.music.desktop.ui.navigation.PlaceholderDestination

val LocalDesktopWindowState = staticCompositionLocalOf<WindowState?> { null }

@Composable
fun DesktopShell(
  state: DesktopShellState,
  onCloseRequest: () -> Unit,
  modifier: Modifier = Modifier,
  windowState: WindowState? = null,
  onEnterImmersive: () -> Unit = {},
  onOpenFile: () -> Unit = {},
  onOpenFolder: () -> Unit = {},
  onRescanLocalFolders: () -> Unit = {},
  onExportLibrary: () -> Unit = {},
  content: @Composable (DesktopDestination) -> Unit = { PlaceholderDestination(it) },
  lyricsContent: @Composable () -> Unit = {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Lyrics will appear here", style = MaterialTheme.typography.bodyLarge)
    }
  },
  searchContent: @Composable (String, () -> Unit) -> Unit = { _, _ -> DefaultSearchHint() },
  onSearchSubmitted: (String) -> Unit = {},
) {
  val rootFocusRequester = remember { FocusRequester() }
  CompositionLocalProvider(LocalDesktopWindowState provides windowState) {
    Box(
      modifier =
        modifier.fillMaxSize().focusRequester(rootFocusRequester).focusable().onKeyEvent { event ->
          val action = KeyboardShortcuts.fromEvent(event) ?: return@onKeyEvent false
          dispatchShortcut(
            state,
            action,
            onQuit = onCloseRequest,
            onEnterImmersive = onEnterImmersive,
          )
          true
        }
    ) {
      Column(modifier = Modifier.fillMaxSize()) {
        TopMenuBar(
          state = state,
          modifier = Modifier.fillMaxWidth().height(44.dp),
          onOpenFile = onOpenFile,
          onOpenFolder = onOpenFolder,
          onRescanLocalFolders = onRescanLocalFolders,
          onExportLibrary = onExportLibrary,
          onQuit = onCloseRequest,
          onEnterImmersive = onEnterImmersive,
          onCloseRequest = onCloseRequest,
        )
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
          AnimatedVisibility(
            visible = state.sidebarExpanded,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
          ) {
            Sidebar(
              selected = state.selectedDestination,
              onDestinationSelected = { state.selectedDestination = it },
              expanded = state.sidebarExpanded,
            )
          }
          Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            content(state.selectedDestination)
          }
          AnimatedVisibility(
            visible = state.rightPanelVisible,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
          ) {
            QueueLyricsPanel(state = state, lyricsContent = lyricsContent)
          }
        }
        BottomPlayerBar(state = state, onEnterImmersive = onEnterImmersive)
      }
      if (state.searchOpen) {
        SearchOverlay(
          onClose = { state.searchOpen = false },
          onRootFocusRequested = { rootFocusRequester.requestFocus() },
          content = searchContent,
          onSearchSubmitted = onSearchSubmitted,
        )
      }
    }
  }
  LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }
  LaunchedEffect(state.searchOpen) {
    if (!state.searchOpen) {
      rootFocusRequester.requestFocus()
    }
  }
}

@Composable
private fun SearchOverlay(
  onClose: () -> Unit,
  onRootFocusRequested: () -> Unit,
  content: @Composable (String, () -> Unit) -> Unit,
  onSearchSubmitted: (String) -> Unit,
) {
  var query by remember { mutableStateOf("") }
  val textFieldFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { textFieldFocusRequester.requestFocus() }
  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onClose,
        ),
    contentAlignment = Alignment.TopCenter,
  ) {
    Surface(
      modifier =
        Modifier.padding(top = 72.dp).width(640.dp).onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          when (event.key) {
            Key.Escape -> {
              onClose()
              true
            }
            else -> false
          }
        },
      shape = MaterialTheme.shapes.large,
      tonalElevation = 6.dp,
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search songs, albums, artists…") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().focusRequester(textFieldFocusRequester).focusable(),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onSearchSubmitted(query) }),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
          content(query, onClose)
        }
      }
    }
  }
}

@Composable
private fun DefaultSearchHint() {
  Text(
    text = "Results will appear here",
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
  )
}
