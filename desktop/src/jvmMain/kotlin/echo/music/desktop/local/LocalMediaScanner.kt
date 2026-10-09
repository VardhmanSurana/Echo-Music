package echo.music.desktop.local

import echo.music.playback.PlaybackMediaItem
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

data class LocalAudioFile(
  val path: String,
  val title: String,
  val artist: String?,
  val album: String?,
  val durationMs: Long,
  val trackNumber: Int?,
  val year: Int?,
  val artworkBytes: ByteArray?,
) {
  fun toPlaybackItem(): PlaybackMediaItem =
    PlaybackMediaItem(
      id = path,
      title = title,
      artist = artist.orEmpty(),
      album = album.orEmpty(),
      artworkUrl = null,
      durationMs = durationMs,
      source = PlaybackMediaItem.Source.LocalFile(path),
    )
}

data class ScanResult(
  val files: List<LocalAudioFile>,
  val errors: List<String>,
  val scannedDirs: List<String>,
) {
  fun toPlaybackItems(): List<PlaybackMediaItem> = files.map { it.toPlaybackItem() }
}

class LocalMediaScanner(
  initialRoots: List<String> = emptyList(),
  private val preferences: DesktopPreferences? = null,
) {
  private val _roots =
    MutableStateFlow(initialRoots.map { it.trim() }.filter { it.isNotEmpty() }.distinct())
  val roots: StateFlow<List<String>> = _roots.asStateFlow()

  private val _result = MutableStateFlow(ScanResult(emptyList(), emptyList(), emptyList()))
  val result: StateFlow<ScanResult> = _result.asStateFlow()

  private val _isScanning = MutableStateFlow(false)
  val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var scanJob: Job? = null
  private val generation = AtomicInteger()

  init {
    runCatching { AudioFileIO.logger.level = Level.OFF }
  }

  fun addRoot(dir: String) {
    val normalized = dir.trim()
    if (normalized.isEmpty()) return
    val updated = (_roots.value + normalized).distinct()
    if (updated == _roots.value) return
    _roots.value = updated
    persistRoots()
    rescan()
  }

  fun removeRoot(dir: String) {
    val updated = _roots.value - dir
    if (updated == _roots.value) return
    _roots.value = updated
    persistRoots()
    rescan()
  }

  fun rescan() {
    scanJob?.cancel()
    val gen = generation.incrementAndGet()
    val rootsSnapshot = _roots.value.toList()
    _isScanning.value = true
    scanJob = scope.launch {
      try {
        val scanned = scanRoots(rootsSnapshot)
        if (generation.get() == gen) {
          _result.value = scanned
        }
      } catch (t: Throwable) {
        if (generation.get() == gen) {
          _result.value =
            ScanResult(emptyList(), listOf(t.message ?: t.javaClass.simpleName), rootsSnapshot)
        }
      } finally {
        if (generation.get() == gen) {
          _isScanning.value = false
        }
      }
    }
  }

  fun toPlaybackItems(): List<PlaybackMediaItem> = _result.value.toPlaybackItems()

  private fun persistRoots() {
    runCatching { preferences?.saveWatchedDirs(_roots.value) }
  }

  private fun scanRoots(roots: List<String>): ScanResult {
    val files = mutableListOf<LocalAudioFile>()
    val errors = mutableListOf<String>()
    val scannedDirs = mutableListOf<String>()
    for (root in roots) {
      val rootPath = Paths.get(root)
      if (!Files.isDirectory(rootPath)) {
        errors += "Not a directory: $root"
        continue
      }
      try {
        Files.walkFileTree(
          rootPath,
          object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
              if (dir != rootPath) {
                if (Files.isSymbolicLink(dir)) return FileVisitResult.SKIP_SUBTREE
                if (dir.fileName.toString().startsWith(".")) return FileVisitResult.SKIP_SUBTREE
              }
              scannedDirs += dir.toString()
              return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
              if (attrs.isRegularFile) {
                val name = file.fileName.toString()
                if (name.startsWith(".")) return FileVisitResult.CONTINUE
                if (isAudioFile(name)) {
                  when (val parsed = readFile(file)) {
                    is ParsedAudio.ParsedFile -> files += parsed.file
                    is ParsedAudio.ParsedError -> errors += parsed.message
                  }
                }
              }
              return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exc: java.io.IOException): FileVisitResult {
              errors += "${file.fileName}: ${exc.message ?: exc.javaClass.simpleName}"
              return FileVisitResult.CONTINUE
            }
          },
        )
      } catch (t: Throwable) {
        errors += "Failed to scan $root: ${t.message ?: t.javaClass.simpleName}"
      }
    }
    files.sortWith(
      compareBy(
        { it.artist ?: "" },
        { it.album ?: "" },
        { it.trackNumber ?: Int.MAX_VALUE },
        { it.title },
      )
    )
    return ScanResult(files, errors, scannedDirs)
  }

  private sealed interface ParsedAudio {
    data class ParsedFile(val file: LocalAudioFile) : ParsedAudio

    data class ParsedError(val message: String) : ParsedAudio
  }

  private fun readFile(path: Path): ParsedAudio {
    val result = runCatching {
      val audioFile = AudioFileIO.read(path.toFile())
      val tag = audioFile.tag
      val fallbackTitle = path.fileName.toString().substringBeforeLast('.')
      ParsedAudio.ParsedFile(
        LocalAudioFile(
          path = path.toAbsolutePath().toString(),
          title =
            tag?.getFirst(FieldKey.TITLE)?.trim().takeUnless { it.isNullOrEmpty() }
              ?: fallbackTitle,
          artist = tag?.getFirst(FieldKey.ARTIST)?.trim().takeUnless { it.isNullOrEmpty() },
          album = tag?.getFirst(FieldKey.ALBUM)?.trim().takeUnless { it.isNullOrEmpty() },
          durationMs = audioFile.audioHeader.trackLength.coerceAtLeast(0) * 1000L,
          trackNumber =
            tag?.getFirst(FieldKey.TRACK)?.trim()?.takeUnless { it.isNullOrEmpty() }?.toIntOrNull(),
          year =
            tag?.getFirst(FieldKey.YEAR)?.trim()?.takeUnless { it.isNullOrEmpty() }?.toIntOrNull(),
          artworkBytes = runCatching { tag?.firstArtwork?.binaryData }.getOrNull(),
        )
      )
    }
    return result.getOrElse {
      ParsedAudio.ParsedError("${path.fileName}: ${it.message ?: it.javaClass.simpleName}")
    }
  }

  companion object {
    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "m4a", "aac", "wav")

    fun isAudioFile(name: String): Boolean =
      name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
  }
}
