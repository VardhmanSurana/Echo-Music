package echo.music.desktop.playback

import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.response.PlayerResponse
import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackMediaItem
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DesktopPlaybackResolver(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
  private data class CachedStream(
    val source: PlaybackMediaItem.Source.StreamUrl,
    val expiresAtMs: Long,
  )

  private val cache = ConcurrentHashMap<String, CachedStream>()

  private val _lastError = MutableStateFlow<String?>(null)
  val lastError: StateFlow<String?> = _lastError.asStateFlow()

  fun songToItem(song: SongItem): PlaybackMediaItem =
    PlaybackMediaItem(
      id = song.id,
      title = song.title,
      artist = song.artists.joinToString(", ") { it.name },
      album = song.album?.name ?: "",
      artworkUrl = song.thumbnail,
      durationMs = (song.duration ?: 0) * 1000L,
      source = PlaybackMediaItem.Source.StreamUrl(""),
    )

  fun playSong(song: SongItem) {
    scope.launch {
      val source = resolveStream(song.id)
      if (source == null) {
        _lastError.value = "Unable to resolve stream for \"${song.title}\""
        return@launch
      }
      _lastError.value = null
      PlaybackManager.playItem(songToItem(song).copy(source = source))
    }
  }

  fun playQueue(songs: List<SongItem>, startIndex: Int) {
    if (songs.isEmpty()) return
    scope.launch {
      val resolved = resolveQueue(songs, startIndex)
      if (resolved == null) {
        _lastError.value = "Unable to resolve streams for ${songs.size} items"
        return@launch
      }
      _lastError.value = null
      PlaybackManager.playQueue(resolved.first, resolved.second)
    }
  }

  fun showError(message: String) {
    _lastError.value = message
  }

  suspend fun resolveStream(videoId: String): PlaybackMediaItem.Source.StreamUrl? {
    val now = System.currentTimeMillis()
    cache[videoId]?.let { cached ->
      if (cached.expiresAtMs > now) return cached.source
    }
    for (client in STREAM_CLIENTS) {
      val response =
        runCatching { YouTube.player(videoId = videoId, client = client) }.getOrNull()?.getOrNull()
          ?: continue
      if (response.playabilityStatus.status != "OK") continue
      val streamingData = response.streamingData ?: continue
      val format = pickAudioFormat(streamingData.adaptiveFormats) ?: continue
      val url = format.url?.takeIf { it.isNotBlank() } ?: continue
      val ttlSeconds = streamingData.expiresInSeconds.coerceIn(60, MAX_TTL_SECONDS)
      val source = PlaybackMediaItem.Source.StreamUrl(url, streamHeaders(client))
      cache[videoId] = CachedStream(source, now + ttlSeconds * 1000L)
      return source
    }
    val newPipeStreams = runCatching {
      YouTube.getNewPipeStreamUrls(videoId)
    }
      .getOrDefault(emptyList())
    val url = pickNewPipeAudioUrl(newPipeStreams) ?: return null
    return PlaybackMediaItem.Source.StreamUrl(url, NEW_PIPE_HEADERS)
  }

  private suspend fun resolveQueue(
    songs: List<SongItem>,
    startIndex: Int,
  ): Pair<List<PlaybackMediaItem>, Int>? = coroutineScope {
    val semaphore = Semaphore(MAX_PARALLEL_RESOLUTIONS)
    val resolved = arrayOfNulls<PlaybackMediaItem>(songs.size)
    val failures = AtomicInteger(0)
    val jobs = songs.mapIndexed { index, song ->
      async {
        semaphore.withPermit {
          val source = runCatching { resolveStream(song.id) }.getOrNull()
          if (source != null) {
            resolved[index] = songToItem(song).copy(source = source)
          } else {
            failures.incrementAndGet()
          }
          Unit
        }
      }
    }
    jobs.awaitAll()
    val items = resolved.filterNotNull()
    if (items.isEmpty()) return@coroutineScope null
    if (failures.get() > 0) {
      _lastError.value = "Skipped ${failures.get()} unresolvable item(s)"
    }
    val originalIndex =
      songs.getOrNull(startIndex)?.let { song -> items.indexOfFirst { it.id == song.id } } ?: -1
    items to originalIndex.coerceAtLeast(0)
  }

  private fun pickAudioFormat(
    formats: List<PlayerResponse.StreamingData.Format>
  ): PlayerResponse.StreamingData.Format? =
    formats
      .filter { it.isAudio && !it.url.isNullOrBlank() }
      .maxWithOrNull(
        compareBy<PlayerResponse.StreamingData.Format>(
          { if (it.mimeType.startsWith("audio/mp4")) 1 else 0 },
          { it.bitrate },
        )
      )

  private fun pickNewPipeAudioUrl(streams: List<Pair<Int, String>>): String? =
    NEW_PIPE_AUDIO_ITAG_PRIORITY.firstNotNullOfOrNull { itag ->
      streams
        .firstOrNull { (streamItag, _) -> streamItag == itag }
        ?.second
        ?.takeIf { it.isNotBlank() }
    }

  companion object {
    private val STREAM_CLIENTS =
      listOf(VISIONOS, ANDROID_VR_1_65_10, TVHTML5, ANDROID_VR_1_43_32, IPADOS, IOS)

    private val NEW_PIPE_AUDIO_ITAG_PRIORITY = listOf(140, 251, 250, 249, 599, 139)

    private const val MAX_TTL_SECONDS = 3600
    private const val MAX_PARALLEL_RESOLUTIONS = 4

    private fun streamHeaders(client: YouTubeClient): Map<String, String> =
      mapOf(
        "User-Agent" to client.userAgent,
        "Referer" to YouTubeClient.REFERER_YOUTUBE_MUSIC,
      )

    private val NEW_PIPE_HEADERS: Map<String, String> =
      mapOf(
        "User-Agent" to YouTubeClient.USER_AGENT_WEB,
        "Referer" to YouTubeClient.REFERER_YOUTUBE_MUSIC,
      )
  }
}
