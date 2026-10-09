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

  var equalizerEnabled: Boolean
    get() = node.getBoolean(KEY_EQ_ENABLED, false)
    set(value) {
      node.putBoolean(KEY_EQ_ENABLED, value)
    }

  var equalizerPreset: String
    get() = node.get(KEY_EQ_PRESET, "Flat")
    set(value) {
      node.put(KEY_EQ_PRESET, value)
    }

  var equalizerBands: List<Float>
    get() {
      val raw = node.get(KEY_EQ_BANDS, null) ?: return List(10) { 0f }
      return raw.split(',').mapNotNull { it.toFloatOrNull() }.takeIf { it.size == 10 }
        ?: List(10) { 0f }
    }
    set(value) {
      node.put(KEY_EQ_BANDS, value.joinToString(","))
    }

  var playbackSpeed: Float
    get() = node.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    set(value) {
      node.putFloat(KEY_PLAYBACK_SPEED, value)
    }

  var bassBoost: Float
    get() = node.getFloat(KEY_BASS_BOOST, 0f)
    set(value) {
      node.putFloat(KEY_BASS_BOOST, value)
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
    private const val KEY_EQ_ENABLED = "eqEnabled"
    private const val KEY_EQ_PRESET = "eqPreset"
    private const val KEY_EQ_BANDS = "eqBands"
    private const val KEY_PLAYBACK_SPEED = "playbackSpeed"
    private const val KEY_BASS_BOOST = "bassBoost"
  }
}
