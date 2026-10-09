package echo.music.desktop.local

import java.util.prefs.Preferences

class DesktopPreferences(private val node: Preferences = Preferences.userRoot().node(NODE)) {
  fun watchedDirs(): List<String> =
    node.get(KEY_WATCHED_DIRS, "").split('\n').filter { it.isNotBlank() }

  fun saveWatchedDirs(dirs: List<String>) {
    node.put(KEY_WATCHED_DIRS, dirs.joinToString("\n"))
  }

  var themeMode: String?
    get() = node.get(KEY_THEME_MODE, null)
    set(value) {
      putOrRemove(KEY_THEME_MODE, value)
    }

  var engineType: String?
    get() = node.get(KEY_ENGINE_TYPE, null)
    set(value) {
      putOrRemove(KEY_ENGINE_TYPE, value)
    }

  private fun putOrRemove(key: String, value: String?) {
    if (value == null) {
      node.remove(key)
    } else {
      node.put(key, value)
    }
  }

  companion object {
    const val NODE = "echo-music/desktop/local"
    private const val KEY_WATCHED_DIRS = "watchedDirs"
    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_ENGINE_TYPE = "engineType"
  }
}
