package echo.music.desktop.system

import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackState
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.Image
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TrayManager(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

  private var trayIcon: TrayIcon? = null
  private var observeJob: Job? = null

  fun install(onOpen: () -> Unit, onQuit: () -> Unit) {
    if (trayIcon != null) return
    if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) return

    val nowPlaying = MenuItem("Now Playing: None")
    nowPlaying.isEnabled = false

    val playPause = MenuItem("Play")
    playPause.addActionListener { PlaybackManager.togglePlay() }
    val next = MenuItem("Next")
    next.addActionListener { PlaybackManager.next() }
    val previous = MenuItem("Previous")
    previous.addActionListener { PlaybackManager.previous() }

    val open = MenuItem("Open")
    open.addActionListener { onOpen() }
    val quit = MenuItem("Quit")
    quit.addActionListener { onQuit() }

    val menu = PopupMenu()
    menu.add(nowPlaying)
    menu.addSeparator()
    menu.add(playPause)
    menu.add(next)
    menu.add(previous)
    menu.addSeparator()
    menu.add(open)
    menu.add(quit)

    val icon = TrayIcon(createTrayImage(), "Echo Music", menu)
    icon.setImageAutoSize(true)
    icon.addActionListener { onOpen() }
    try {
      SystemTray.getSystemTray().add(icon)
    } catch (_: Exception) {
      return
    }
    trayIcon = icon
    observeJob = scope.launch {
      combine(PlaybackManager.currentItem, PlaybackManager.state) { item, state ->
          item to state
        }
        .collect { (item, state) ->
          nowPlaying.label =
            when {
              item == null -> "Now Playing: None"
              item.artist.isBlank() -> "Now Playing: ${item.title}"
              else -> "Now Playing: ${item.title} - ${item.artist}"
            }
          playPause.label = if (state == PlaybackState.Playing) "Pause" else "Play"
        }
    }
  }

  fun dispose() {
    observeJob?.cancel()
    observeJob = null
    val icon = trayIcon ?: return
    trayIcon = null
    try {
      SystemTray.getSystemTray().remove(icon)
    } catch (_: Exception) {}
  }

  private fun createTrayImage(): Image {
    val size = 32
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      graphics.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON,
      )
      graphics.color = Color(0x1e, 0x88, 0xe5)
      graphics.fillOval(2, 2, size - 4, size - 4)
      graphics.color = Color.WHITE
      val triangle = Path2D.Float()
      triangle.moveTo(12f, 9f)
      triangle.lineTo(23f, 16f)
      triangle.lineTo(12f, 23f)
      triangle.closePath()
      graphics.fill(triangle)
    } finally {
      graphics.dispose()
    }
    return image
  }
}
