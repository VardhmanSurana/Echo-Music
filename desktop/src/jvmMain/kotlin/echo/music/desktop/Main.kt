package echo.music.desktop

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.music.innertube.YouTube
import echo.music.desktop.auth.AuthSyncState
import echo.music.desktop.auth.LocalAuthServer
import echo.music.desktop.local.DesktopPreferences
import echo.music.desktop.local.LocalMediaScanner
import echo.music.desktop.lyrics.DesktopLyricsResolver
import echo.music.desktop.playback.DesktopPlaybackResolver
import echo.music.desktop.system.MprisService
import echo.music.desktop.system.MutableWindowVisibilitySource
import echo.music.desktop.system.PerformanceGovernor
import echo.music.desktop.system.TrayManager
import echo.music.desktop.ui.components.MiniPlayer
import echo.music.desktop.ui.components.SyncedLyricsView
import echo.music.desktop.ui.navigation.DesktopDestination
import echo.music.desktop.ui.navigation.PlaceholderDestination
import echo.music.desktop.ui.screens.EqualizerScreen
import echo.music.desktop.ui.screens.ExploreScreen
import echo.music.desktop.ui.screens.ExploreStateHolder
import echo.music.desktop.ui.screens.HomeScreen
import echo.music.desktop.ui.screens.HomeStateHolder
import echo.music.desktop.ui.screens.ImmersiveCanvasScreen
import echo.music.desktop.ui.screens.LibraryScreen
import echo.music.desktop.ui.screens.LibraryStateHolder
import echo.music.desktop.ui.screens.LibraryTab
import echo.music.desktop.ui.screens.LocalMusicScreen
import echo.music.desktop.ui.screens.SearchOverlayContent
import echo.music.desktop.ui.screens.SearchStateHolder
import echo.music.desktop.ui.screens.SettingsScreen
import echo.music.desktop.ui.screens.chooseDirectory
import echo.music.desktop.ui.shell.DesktopShell
import echo.music.desktop.ui.shell.DesktopShellState
import echo.music.desktop.ui.theme.LocalRenderBudget
import echo.music.desktop.ui.theme.Theme
import echo.music.desktop.ui.theme.ThemeMode
import echo.music.desktop.ui.theme.ThemeSettings
import echo.music.playback.EngineType
import echo.music.playback.PlaybackManager
import kotlinx.coroutines.launch

fun main() = application {
  val windowState =
    rememberWindowState(
      size = DpSize(1280.dp, 800.dp),
      position = WindowPosition.Aligned(Alignment.Center),
    )
  val shellState = remember { DesktopShellState() }
  val appScope = rememberCoroutineScope()

  val preferences = remember { DesktopPreferences() }
  val scanner = remember {
    LocalMediaScanner(
      initialRoots = runCatching { preferences.watchedDirs() }.getOrDefault(emptyList()),
      preferences = preferences,
    )
  }
  val resolver = remember {
    DesktopPlaybackResolver(appScope).apply {
      onQueueOpened = {
        shellState.rightPanelVisible = true
        shellState.rightPanelTab = echo.music.desktop.ui.shell.RightPanelTab.QUEUE
      }
    }
  }
  val lyricsResolver = remember { DesktopLyricsResolver(appScope) }
  val homeState = remember { HomeStateHolder(appScope, resolver) }
  val exploreState = remember { ExploreStateHolder(appScope, resolver) }
  val libraryState = remember { LibraryStateHolder(appScope, resolver) }
  val searchState = remember { SearchStateHolder(appScope, resolver) }

  val bringToFront = remember { mutableStateOf(false) }
  val exitRequested = remember { mutableStateOf(false) }

  val authServer = remember {
    LocalAuthServer().apply {
      cookieSink = { payload ->
        val cookieString = payload.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        YouTube.cookie = cookieString
        payload.visitorData?.let { YouTube.visitorData = it }
        payload.dataSyncId?.let { YouTube.dataSyncId = it }

        preferences.authCookie = cookieString
        preferences.authVisitorData = payload.visitorData
        preferences.authDataSyncId = payload.dataSyncId

        appScope.launch {
          YouTube.accountInfo()
            .onSuccess { info ->
              preferences.authAccountName = info.name
              preferences.authAccountEmail = info.email
              preferences.authAvatarUrl = info.thumbnailUrl
              AuthSyncState.updateAccount(info.name, info.email, info.thumbnailUrl)
            }
            .onFailure {
              System.err.println("Could not resolve account info: ${it.message}")
            }
        }
      }
    }
  }
  val trayManager = remember { TrayManager() }
  val visibilitySource = remember { MutableWindowVisibilitySource() }
  val governor = remember {
    PerformanceGovernor(visibilitySource, appScope, PerformanceGovernor.DEFAULT_DEBOUNCE_MILLIS) {}
  }
  val mprisService = remember {
    MprisService(
      onRaise = { bringToFront.value = true },
      onQuit = { exitRequested.value = true },
    )
  }

  var immersiveVisible by remember { mutableStateOf(false) }

  LaunchedEffect(lyricsResolver) { lyricsResolver.start() }

  LaunchedEffect(Unit) {
    // Restore persisted YouTube Music session if present
    preferences.authCookie
      ?.takeIf { it.isNotBlank() }
      ?.let { savedCookie ->
        YouTube.cookie = savedCookie
        preferences.authVisitorData?.let { YouTube.visitorData = it }
        preferences.authDataSyncId?.let { YouTube.dataSyncId = it }

        val cookiePairs =
          savedCookie
            .split(";")
            .mapNotNull<String, Pair<String, String>> { part ->
              val trimmed = part.trim()
              val eq = trimmed.indexOf('=')
              if (eq > 0) Pair(trimmed.substring(0, eq), trimmed.substring(eq + 1)) else null
            }
            .toMap()

        AuthSyncState.recordSync(
          cookies = cookiePairs,
          accountName = preferences.authAccountName,
          accountEmail = preferences.authAccountEmail,
          avatarUrl = preferences.authAvatarUrl,
        )

        appScope.launch {
          YouTube.accountInfo().onSuccess { info ->
            preferences.authAccountName = info.name
            preferences.authAccountEmail = info.email
            preferences.authAvatarUrl = info.thumbnailUrl
            AuthSyncState.updateAccount(info.name, info.email, info.thumbnailUrl)
          }
        }
      }

    runCatching { authServer.start() }
      .onFailure { System.err.println("LocalAuthServer failed to start: ${it.message}") }
    if (preferences.mprisEnabled) {
      runCatching { mprisService.start() }
        .onFailure { System.err.println("MprisService failed to start: ${it.message}") }
    }
    trayManager.install(
      onOpen = {
        windowState.isMinimized = false
        bringToFront.value = true
      },
      onQuit = { exitRequested.value = true },
    )
    val persistedTheme =
      preferences.themeMode?.let { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrNull() }
    if (persistedTheme != null) {
      ThemeSettings.setMode(persistedTheme)
    }
    ThemeSettings.setPureBlack(preferences.pureBlack)
    ThemeSettings.setSeedColor(androidx.compose.ui.graphics.Color(preferences.accentColor))

    val persistedEngine =
      preferences.engineType?.let { stored ->
        runCatching { EngineType.valueOf(stored) }.getOrNull()
      }
    PlaybackManager.setEngine(persistedEngine ?: EngineType.AUTO)
  }

  LaunchedEffect(exitRequested.value) {
    if (!exitRequested.value) return@LaunchedEffect
    runCatching { authServer.stop() }
      .onFailure { System.err.println("LocalAuthServer stop failed: ${it.message}") }
    mprisService.release()
    trayManager.dispose()
    governor.stop()
    PlaybackManager.pause()
    exitApplication()
  }

  val handleCloseRequest: () -> Unit = {
    if (preferences.closeToTray) {
      windowState.isMinimized = true
    } else {
      exitRequested.value = true
    }
  }

  Window(
    onCloseRequest = handleCloseRequest,
    state = windowState,
    title = "Echo Music",
  ) {
    window.minimumSize = java.awt.Dimension(960, 600)
    LaunchedEffect(windowState) {
      snapshotFlow { windowState.isMinimized }.collect { visibilitySource.setMinimized(it) }
    }
    LaunchedEffect(bringToFront.value) {
      if (bringToFront.value) {
        window.toFront()
        bringToFront.value = false
      }
    }
    CompositionLocalProvider(LocalRenderBudget provides governor.budget.collectAsState().value) {
      Theme {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = androidx.compose.material3.MaterialTheme.colorScheme.background,
        ) {
          DesktopShell(
            state = shellState,
            windowState = windowState,
            onCloseRequest = handleCloseRequest,
            onEnterImmersive = { immersiveVisible = !immersiveVisible },
            onOpenFolder = { chooseDirectory()?.let { scanner.addRoot(it) } },
            onRescanLocalFolders = { scanner.rescan() },
            content = { destination ->
              when (destination) {
                DesktopDestination.HOME -> HomeScreen(homeState)
                DesktopDestination.EXPLORE -> ExploreScreen(exploreState)
                DesktopDestination.LIBRARY ->
                  LibraryScreen(libraryState, initialTab = LibraryTab.PLAYLISTS)
                DesktopDestination.LIKED_SONGS ->
                  LibraryScreen(libraryState, initialTab = LibraryTab.LIKED_SONGS)
                DesktopDestination.PLAYLISTS ->
                  LibraryScreen(libraryState, initialTab = LibraryTab.PLAYLISTS)
                DesktopDestination.ARTISTS -> LibraryScreen(libraryState)
                DesktopDestination.LOCAL_LIBRARY -> LocalMusicScreen(scanner)
                DesktopDestination.FOLDERS -> LocalMusicScreen(scanner)
                DesktopDestination.SETTINGS -> SettingsScreen(preferences, scanner)
                DesktopDestination.DOWNLOADED_OFFLINE -> PlaceholderDestination(destination)
                DesktopDestination.EQUALIZER -> EqualizerScreen(preferences)
              }
            },
            lyricsContent = {
              SyncedLyricsView(
                resolver = lyricsResolver,
                compact = true,
                modifier = Modifier.fillMaxSize(),
              )
            },
            searchContent = { query, onClose -> SearchOverlayContent(query, searchState, onClose) },
            onSearchSubmitted = { searchState.playFirstResult() },
          )
        }
      }
    }
  }

  if (immersiveVisible) {
    ImmersiveWindow(onExit = { immersiveVisible = false }, lyricsResolver = lyricsResolver)
  }

  if (shellState.miniPlayerVisible) {
    MiniPlayerWindow(onClose = { shellState.miniPlayerVisible = false })
  }
}

@Composable
private fun ImmersiveWindow(onExit: () -> Unit, lyricsResolver: DesktopLyricsResolver) {
  val state = remember { WindowState(placement = WindowPlacement.Fullscreen) }
  Window(onCloseRequest = onExit, state = state, title = "Echo Music", undecorated = true) {
    Surface(
      modifier =
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
          if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
          if (event.key == Key.F11 || event.key == Key.Escape) {
            onExit()
            true
          } else {
            false
          }
        },
      color = Color.Black,
    ) {
      Theme { ImmersiveCanvasScreen(onExit = onExit, lyricsResolver = lyricsResolver) }
    }
  }
}

@Composable
private fun MiniPlayerWindow(onClose: () -> Unit) {
  val state = remember {
    WindowState(position = WindowPosition(Alignment.BottomEnd), size = DpSize(320.dp, 96.dp))
  }
  Window(
    onCloseRequest = onClose,
    state = state,
    title = "Echo Music Mini Player",
    undecorated = true,
    alwaysOnTop = true,
    resizable = false,
  ) {
    Theme {
      MiniPlayer(onClose = onClose)
    }
  }
}
