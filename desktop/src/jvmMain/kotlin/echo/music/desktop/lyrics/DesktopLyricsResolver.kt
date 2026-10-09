package echo.music.desktop.lyrics

import com.music.echo.unison.Unison
import com.music.kugou.KuGou
import com.music.lrclib.LrcLib
import com.music.simpmusic.SimpMusicLyrics
import com.music.youlyplus.YouLyPlus
import echo.music.iad1tya.betterlyrics.BetterLyrics
import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackMediaItem
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopLyricsResolver(private val scope: CoroutineScope) {

  private val _current = MutableStateFlow<LyricsResult?>(null)
  val current: StateFlow<LyricsResult?> = _current.asStateFlow()

  private val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  private val _lastProvider = MutableStateFlow<String?>(null)
  val lastProvider: StateFlow<String?> = _lastProvider.asStateFlow()

  private val cache = ConcurrentHashMap<String, LyricsResult>()

  private var loadJob: Job? = null
  private var observationJob: Job? = null
  private var lastItemId: String? = null

  fun start() {
    if (observationJob != null) return
    observationJob = scope.launch {
      PlaybackManager.currentItem.collect { item ->
        if (item?.id != lastItemId) loadFor(item)
      }
    }
  }

  fun loadFor(item: PlaybackMediaItem?) {
    loadJob?.cancel()
    if (item == null) {
      lastItemId = null
      _current.value = null
      _loading.value = false
      return
    }
    lastItemId = item.id
    cache[item.id]?.let { cached ->
      _current.value = cached
      _loading.value = false
      return
    }
    _current.value = null
    _loading.value = true
    loadJob = scope.launch {
      val result = resolve(item)
      _lastProvider.value = result?.provider
      if (result != null) cache[item.id] = result
      _current.value = result
      _loading.value = false
    }
  }

  private suspend fun resolve(item: PlaybackMediaItem): LyricsResult? {
    val title = item.title.trim()
    if (title.isEmpty()) return null
    val artist = item.artist.trim()
    val album = item.album.trim().takeIf { it.isNotEmpty() }
    val durationSeconds = (item.durationMs / 1000).toInt().takeIf { it > 0 } ?: -1
    val videoId = videoIdRegex.matchEntire(item.id)?.value
    for (provider in providers) {
      if (!provider.usable(title, artist, videoId)) continue
      val text = runCatching {
        provider.fetch(title, artist, album, durationSeconds, videoId)
      }
        .getOrNull()
      val result = toResult(provider.name, text)
      if (result != null) return result
    }
    return null
  }

  private fun toResult(provider: String, text: String?): LyricsResult? {
    if (text.isNullOrBlank()) return null
    val trimmed = text.trim()
    val timed = LrcParser.parseLrc(trimmed)
    if (timed.isNotEmpty()) return LyricsResult(timed = timed, plain = null, provider = provider)
    if (looksInstrumental(trimmed)) {
      return LyricsResult(timed = null, plain = null, provider = provider, isInstrumental = true)
    }
    return LyricsResult(timed = null, plain = trimmed, provider = provider)
  }

  private fun looksInstrumental(text: String): Boolean =
    text.lines().size <= 2 && text.contains("instrumental", ignoreCase = true)

  private class Provider(
    val name: String,
    val usable: (title: String, artist: String, videoId: String?) -> Boolean,
    val fetch:
      suspend (
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int,
        videoId: String?,
      ) -> String?,
  )

  private val providers =
    listOf(
      Provider(
        name = "BetterLyrics",
        usable = { _, _, _ -> true },
        fetch = { title, artist, album, durationSeconds, _ ->
          BetterLyrics.getLyrics(title, artist, durationSeconds, album).getOrNull()
        },
      ),
      Provider(
        name = "LRCLib",
        usable = { _, _, _ -> true },
        fetch = { title, artist, album, durationSeconds, _ ->
          LrcLib.getLyrics(title, artist, durationSeconds, album).getOrNull()
        },
      ),
      Provider(
        name = "SimpMusic",
        usable = { _, _, videoId -> videoId != null },
        fetch = { _, _, _, durationSeconds, videoId ->
          SimpMusicLyrics.getLyrics(videoId!!, durationSeconds).getOrNull()
        },
      ),
      Provider(
        name = "Unison",
        usable = { _, artist, _ -> artist.isNotEmpty() },
        fetch = { title, artist, album, durationSeconds, videoId ->
          Unison.getLyrics(videoId, title, artist, album, durationSeconds).getOrNull()
        },
      ),
      Provider(
        name = "KuGou",
        usable = { _, artist, _ -> artist.isNotEmpty() },
        fetch = { title, artist, album, durationSeconds, _ ->
          KuGou.getLyrics(title, artist, durationSeconds, album).getOrNull()
        },
      ),
      Provider(
        name = "YouLyPlus",
        usable = { _, artist, _ -> artist.isNotEmpty() },
        fetch = { title, artist, album, durationSeconds, _ ->
          YouLyPlus.getLyrics(title, artist, durationSeconds, album).getOrNull()
        },
      ),
    )

  companion object {
    private val videoIdRegex = Regex("""[A-Za-z0-9_-]{11}""")

    val providerOrder: List<String> =
      listOf("BetterLyrics", "LRCLib", "SimpMusic", "Unison", "KuGou", "YouLyPlus")
  }
}
