package echo.music.desktop.local

import java.util.prefs.Preferences

class DesktopPreferences(private val node: Preferences = Preferences.userRoot().node(NODE)) {

  // Local media scanner directories
  fun watchedDirs(): List<String> =
    node.get(KEY_WATCHED_DIRS, "").split('\n').filter { it.isNotBlank() }

  fun saveWatchedDirs(dirs: List<String>) {
    node.put(KEY_WATCHED_DIRS, dirs.joinToString("\n"))
  }

  // General & Desktop
  var closeToTray: Boolean
    get() = node.getBoolean(KEY_CLOSE_TO_TRAY, false)
    set(value) {
      node.putBoolean(KEY_CLOSE_TO_TRAY, value)
    }

  var startMinimized: Boolean
    get() = node.getBoolean(KEY_START_MINIMIZED, false)
    set(value) {
      node.putBoolean(KEY_START_MINIMIZED, value)
    }

  var mprisEnabled: Boolean
    get() = node.getBoolean(KEY_MPRIS_ENABLED, true)
    set(value) {
      node.putBoolean(KEY_MPRIS_ENABLED, value)
    }

  var desktopNotifications: Boolean
    get() = node.getBoolean(KEY_DESKTOP_NOTIFICATIONS, true)
    set(value) {
      node.putBoolean(KEY_DESKTOP_NOTIFICATIONS, value)
    }

  // YouTube Music Authentication & Session
  var authCookie: String?
    get() = node.get(KEY_AUTH_COOKIE, null)
    set(value) {
      putOrRemove(KEY_AUTH_COOKIE, value)
    }

  var authVisitorData: String?
    get() = node.get(KEY_AUTH_VISITOR_DATA, null)
    set(value) {
      putOrRemove(KEY_AUTH_VISITOR_DATA, value)
    }

  var authDataSyncId: String?
    get() = node.get(KEY_AUTH_DATASYNC_ID, null)
    set(value) {
      putOrRemove(KEY_AUTH_DATASYNC_ID, value)
    }

  var authAccountName: String?
    get() = node.get(KEY_AUTH_ACCOUNT_NAME, null)
    set(value) {
      putOrRemove(KEY_AUTH_ACCOUNT_NAME, value)
    }

  var authAccountEmail: String?
    get() = node.get(KEY_AUTH_ACCOUNT_EMAIL, null)
    set(value) {
      putOrRemove(KEY_AUTH_ACCOUNT_EMAIL, value)
    }

  var authAvatarUrl: String?
    get() = node.get(KEY_AUTH_AVATAR_URL, null)
    set(value) {
      putOrRemove(KEY_AUTH_AVATAR_URL, value)
    }

  fun clearAuth() {
    putOrRemove(KEY_AUTH_COOKIE, null)
    putOrRemove(KEY_AUTH_VISITOR_DATA, null)
    putOrRemove(KEY_AUTH_DATASYNC_ID, null)
    putOrRemove(KEY_AUTH_ACCOUNT_NAME, null)
    putOrRemove(KEY_AUTH_ACCOUNT_EMAIL, null)
    putOrRemove(KEY_AUTH_AVATAR_URL, null)
  }

  // Appearance
  var themeMode: String?
    get() = node.get(KEY_THEME_MODE, null)
    set(value) {
      putOrRemove(KEY_THEME_MODE, value)
    }

  var pureBlack: Boolean
    get() = node.getBoolean(KEY_PURE_BLACK, false)
    set(value) {
      node.putBoolean(KEY_PURE_BLACK, value)
    }

  var accentColor: Long
    get() = node.getLong(KEY_ACCENT_COLOR, 0xFF1E88E5)
    set(value) {
      node.putLong(KEY_ACCENT_COLOR, value)
    }

  var dynamicPalette: Boolean
    get() = node.getBoolean(KEY_DYNAMIC_PALETTE, true)
    set(value) {
      node.putBoolean(KEY_DYNAMIC_PALETTE, value)
    }

  var uiDensity: String
    get() = node.get(KEY_UI_DENSITY, "standard")
    set(value) {
      node.put(KEY_UI_DENSITY, value)
    }

  var playerBackgroundStyle: String
    get() = node.get(KEY_PLAYER_BG_STYLE, "GRADIENT")
    set(value) {
      node.put(KEY_PLAYER_BG_STYLE, value)
    }

  var thumbnailCornerRadiusDp: Int
    get() = node.getInt(KEY_THUMBNAIL_CORNER_RADIUS, 12)
    set(value) {
      node.putInt(KEY_THUMBNAIL_CORNER_RADIUS, value)
    }

  var cropAlbumArt: Boolean
    get() = node.getBoolean(KEY_CROP_ALBUM_ART, false)
    set(value) {
      node.putBoolean(KEY_CROP_ALBUM_ART, value)
    }

  var hidePlayerThumbnail: Boolean
    get() = node.getBoolean(KEY_HIDE_PLAYER_THUMBNAIL, false)
    set(value) {
      node.putBoolean(KEY_HIDE_PLAYER_THUMBNAIL, value)
    }

  var lyricsBlur: Boolean
    get() = node.getBoolean(KEY_LYRICS_BLUR, true)
    set(value) {
      node.putBoolean(KEY_LYRICS_BLUR, value)
    }

  var lyricsTextSize: Float
    get() = node.getFloat(KEY_LYRICS_TEXT_SIZE, 22f)
    set(value) {
      node.putFloat(KEY_LYRICS_TEXT_SIZE, value)
    }

  var lyricsPosition: String
    get() = node.get(KEY_LYRICS_POSITION, "CENTER")
    set(value) {
      node.put(KEY_LYRICS_POSITION, value)
    }

  var lyricsGlow: Boolean
    get() = node.getBoolean(KEY_LYRICS_GLOW, false)
    set(value) {
      node.putBoolean(KEY_LYRICS_GLOW, value)
    }

  // Audio Engine & Equalizer
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

  var volumeNormalization: Boolean
    get() = node.getBoolean(KEY_VOLUME_NORMALIZATION, false)
    set(value) {
      node.putBoolean(KEY_VOLUME_NORMALIZATION, value)
    }

  var crossfadeEnabled: Boolean
    get() = node.getBoolean(KEY_CROSSFADE_ENABLED, false)
    set(value) {
      node.putBoolean(KEY_CROSSFADE_ENABLED, value)
    }

  var crossfadeDurationSeconds: Float
    get() = node.getFloat(KEY_CROSSFADE_SECONDS, 5.0f)
    set(value) {
      node.putFloat(KEY_CROSSFADE_SECONDS, value)
    }

  var skipSilence: Boolean
    get() = node.getBoolean(KEY_SKIP_SILENCE, false)
    set(value) {
      node.putBoolean(KEY_SKIP_SILENCE, value)
    }

  var audioOutputDevice: String
    get() = node.get(KEY_AUDIO_OUTPUT_DEVICE, "Default")
    set(value) {
      node.put(KEY_AUDIO_OUTPUT_DEVICE, value)
    }

  // Content & Localization
  var hideExplicit: Boolean
    get() = node.getBoolean(KEY_HIDE_EXPLICIT, false)
    set(value) {
      node.putBoolean(KEY_HIDE_EXPLICIT, value)
    }

  var hideVideoTracks: Boolean
    get() = node.getBoolean(KEY_HIDE_VIDEO_TRACKS, false)
    set(value) {
      node.putBoolean(KEY_HIDE_VIDEO_TRACKS, value)
    }

  var contentCountry: String
    get() = node.get(KEY_CONTENT_COUNTRY, "US")
    set(value) {
      node.put(KEY_CONTENT_COUNTRY, value)
    }

  var contentLanguage: String
    get() = node.get(KEY_CONTENT_LANGUAGE, "en")
    set(value) {
      node.put(KEY_CONTENT_LANGUAGE, value)
    }

  var autoplaySimilar: Boolean
    get() = node.getBoolean(KEY_AUTOPLAY_SIMILAR, true)
    set(value) {
      node.putBoolean(KEY_AUTOPLAY_SIMILAR, value)
    }

  // Integrations
  var discordRpcEnabled: Boolean
    get() = node.getBoolean(KEY_DISCORD_RPC_ENABLED, false)
    set(value) {
      node.putBoolean(KEY_DISCORD_RPC_ENABLED, value)
    }

  var discordShowCover: Boolean
    get() = node.getBoolean(KEY_DISCORD_SHOW_COVER, true)
    set(value) {
      node.putBoolean(KEY_DISCORD_SHOW_COVER, value)
    }

  var lastFmEnabled: Boolean
    get() = node.getBoolean(KEY_LAST_FM_ENABLED, false)
    set(value) {
      node.putBoolean(KEY_LAST_FM_ENABLED, value)
    }

  var lastFmUsername: String
    get() = node.get(KEY_LAST_FM_USERNAME, "")
    set(value) {
      node.put(KEY_LAST_FM_USERNAME, value)
    }

  var listenBrainzEnabled: Boolean
    get() = node.getBoolean(KEY_LISTEN_BRAINZ_ENABLED, false)
    set(value) {
      node.putBoolean(KEY_LISTEN_BRAINZ_ENABLED, value)
    }

  var listenBrainzToken: String
    get() = node.get(KEY_LISTEN_BRAINZ_TOKEN, "")
    set(value) {
      node.put(KEY_LISTEN_BRAINZ_TOKEN, value)
    }

  // Storage & Cache Limits
  var maxAudioCacheMb: Int
    get() = node.getInt(KEY_MAX_AUDIO_CACHE_MB, 1024)
    set(value) {
      node.putInt(KEY_MAX_AUDIO_CACHE_MB, value)
    }

  var maxImageCacheMb: Int
    get() = node.getInt(KEY_MAX_IMAGE_CACHE_MB, 512)
    set(value) {
      node.putInt(KEY_MAX_IMAGE_CACHE_MB, value)
    }

  // Backup & Export / Import
  fun exportSettingsJson(): String {
    val keys = node.keys()
    val pairs = keys.map { key ->
      val escapedVal =
        node.get(key, "").replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
      "\"$key\": \"$escapedVal\""
    }
    return "{\n  " + pairs.joinToString(",\n  ") + "\n}"
  }

  fun importSettingsJson(json: String): Boolean {
    return runCatching {
        val regex = "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        regex.findAll(json).forEach { match ->
          val (k, v) = match.destructured
          val unescaped = v.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
          node.put(k, unescaped)
        }
        true
      }
      .getOrDefault(false)
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
    private const val KEY_CLOSE_TO_TRAY = "closeToTray"
    private const val KEY_START_MINIMIZED = "startMinimized"
    private const val KEY_MPRIS_ENABLED = "mprisEnabled"
    private const val KEY_DESKTOP_NOTIFICATIONS = "desktopNotifications"
    private const val KEY_THEME_MODE = "themeMode"
    private const val KEY_PURE_BLACK = "pureBlack"
    private const val KEY_ACCENT_COLOR = "accentColor"
    private const val KEY_DYNAMIC_PALETTE = "dynamicPalette"
    private const val KEY_UI_DENSITY = "uiDensity"
    private const val KEY_PLAYER_BG_STYLE = "playerBgStyle"
    private const val KEY_THUMBNAIL_CORNER_RADIUS = "thumbnailCornerRadius"
    private const val KEY_CROP_ALBUM_ART = "cropAlbumArt"
    private const val KEY_HIDE_PLAYER_THUMBNAIL = "hidePlayerThumbnail"
    private const val KEY_LYRICS_BLUR = "lyricsBlur"
    private const val KEY_LYRICS_TEXT_SIZE = "lyricsTextSize"
    private const val KEY_LYRICS_POSITION = "lyricsPosition"
    private const val KEY_LYRICS_GLOW = "lyricsGlow"
    private const val KEY_ENGINE_TYPE = "engineType"
    private const val KEY_EQ_ENABLED = "eqEnabled"
    private const val KEY_EQ_PRESET = "eqPreset"
    private const val KEY_EQ_BANDS = "eqBands"
    private const val KEY_PLAYBACK_SPEED = "playbackSpeed"
    private const val KEY_BASS_BOOST = "bassBoost"
    private const val KEY_VOLUME_NORMALIZATION = "volumeNormalization"
    private const val KEY_CROSSFADE_ENABLED = "crossfadeEnabled"
    private const val KEY_CROSSFADE_SECONDS = "crossfadeSeconds"
    private const val KEY_SKIP_SILENCE = "skipSilence"
    private const val KEY_AUDIO_OUTPUT_DEVICE = "audioOutputDevice"
    private const val KEY_HIDE_EXPLICIT = "hideExplicit"
    private const val KEY_HIDE_VIDEO_TRACKS = "hideVideoTracks"
    private const val KEY_CONTENT_COUNTRY = "contentCountry"
    private const val KEY_CONTENT_LANGUAGE = "contentLanguage"
    private const val KEY_AUTOPLAY_SIMILAR = "autoplaySimilar"
    private const val KEY_DISCORD_RPC_ENABLED = "discordRpcEnabled"
    private const val KEY_DISCORD_SHOW_COVER = "discordShowCover"
    private const val KEY_LAST_FM_ENABLED = "lastFmEnabled"
    private const val KEY_LAST_FM_USERNAME = "lastFmUsername"
    private const val KEY_LISTEN_BRAINZ_ENABLED = "listenBrainzEnabled"
    private const val KEY_LISTEN_BRAINZ_TOKEN = "listenBrainzToken"
    private const val KEY_MAX_AUDIO_CACHE_MB = "maxAudioCacheMb"
    private const val KEY_MAX_IMAGE_CACHE_MB = "maxImageCacheMb"
    private const val KEY_AUTH_COOKIE = "authCookie"
    private const val KEY_AUTH_VISITOR_DATA = "authVisitorData"
    private const val KEY_AUTH_DATASYNC_ID = "authDataSyncId"
    private const val KEY_AUTH_ACCOUNT_NAME = "authAccountName"
    private const val KEY_AUTH_ACCOUNT_EMAIL = "authAccountEmail"
    private const val KEY_AUTH_AVATAR_URL = "authAvatarUrl"
  }
}
