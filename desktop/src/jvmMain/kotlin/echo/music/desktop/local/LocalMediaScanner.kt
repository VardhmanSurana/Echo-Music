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
  fun toPlaybackItem(): PlaybackMediaItem {
    val bytes = artworkBytes
    val artUrl =
      if (bytes != null && bytes.isNotEmpty()) {
        runCatching {
          val cacheDir =
            java.io.File(System.getProperty("user.home"), ".cache/echo-music/art").apply {
              mkdirs()
            }
          val hash = path.hashCode().toUInt().toString(16)
          val artFile = java.io.File(cacheDir, "$hash.jpg")
          if (!artFile.exists() || artFile.length() == 0L) {
            artFile.writeBytes(bytes)
          }
          artFile.toURI().toString()
        }
          .getOrNull()
      } else {
        null
      }

    return PlaybackMediaItem(
      id = path,
      title = title,
      artist = artist.orEmpty(),
      album = album.orEmpty(),
      artworkUrl = artUrl,
      durationMs = durationMs,
      source = PlaybackMediaItem.Source.LocalFile(path),
    )
  }
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
    if (result.isSuccess) {
      return result.getOrThrow()
    }
    val fallback = runCatching {
      val fileName = path.fileName.toString()
      if (
        fileName.endsWith(".m4a", ignoreCase = true) || fileName.endsWith(".aac", ignoreCase = true)
      ) {
        Mp4MetadataReader.read(path)
      } else {
        null
      }
    }
      .getOrNull()
    if (fallback != null) {
      return ParsedAudio.ParsedFile(fallback)
    }
    return ParsedAudio.ParsedError(
      "${path.fileName}: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
    )
  }

  companion object {
    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "ogg", "oga", "m4a", "aac", "wav")

    fun isAudioFile(name: String): Boolean =
      name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS
  }
}

internal object Mp4MetadataReader {
  fun read(path: Path): LocalAudioFile? {
    val file = path.toFile()
    if (!file.exists() || file.length() < 16) return null
    return try {
      java.io.RandomAccessFile(file, "r").use { raf ->
        val length = raf.length()
        var offset = 0L
        var moovOffset = -1L
        var moovSize = 0L

        while (offset + 8 <= length) {
          raf.seek(offset)
          val sizeInt = raf.readInt()
          val typeBytes = ByteArray(4)
          raf.readFully(typeBytes)
          val type = String(typeBytes, Charsets.ISO_8859_1)
          val headerSize: Long
          val boxSize: Long =
            when (sizeInt) {
              1 -> {
                headerSize = 16L
                raf.readLong()
              }
              0 -> {
                headerSize = 8L
                length - offset
              }
              else -> {
                headerSize = 8L
                sizeInt.toLong() and 0xFFFFFFFFL
              }
            }
          if (boxSize <= 0) break
          if (type == "moov") {
            moovOffset = offset + headerSize
            moovSize = boxSize - headerSize
            break
          }
          offset += boxSize
        }

        if (moovOffset < 0 || moovSize <= 0) return null

        val moovBytes = ByteArray(moovSize.coerceAtMost(5 * 1024 * 1024).toInt())
        raf.seek(moovOffset)
        raf.readFully(moovBytes)

        parseMoov(path, moovBytes)
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun parseMoov(path: Path, buf: ByteArray): LocalAudioFile {
    var durationMs = 0L
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var year: Int? = null
    var trackNumber: Int? = null
    var artworkBytes: ByteArray? = null

    val boxes = parseBoxes(buf, 0, buf.size)
    val mvhd = boxes.firstOrNull { it.type == "mvhd" }
    if (mvhd != null && mvhd.size >= 24) {
      val h = mvhd.headerSize
      val off = mvhd.offset
      val version = buf[off + h].toInt()
      if (version == 0 && off + h + 20 <= buf.size) {
        val timeScale = readInt(buf, off + h + 12).toLong() and 0xFFFFFFFFL
        val duration = readInt(buf, off + h + 16).toLong() and 0xFFFFFFFFL
        if (timeScale > 0) durationMs = (duration * 1000L) / timeScale
      } else if (version == 1 && off + h + 28 <= buf.size) {
        val timeScale = readInt(buf, off + h + 20).toLong() and 0xFFFFFFFFL
        val duration = readLong(buf, off + h + 24)
        if (timeScale > 0) durationMs = (duration * 1000L) / timeScale
      }
    }

    val udta = boxes.firstOrNull { it.type == "udta" }
    if (udta != null) {
      val udtaBoxes = parseBoxes(buf, udta.offset + udta.headerSize, udta.offset + udta.size)
      val meta = udtaBoxes.firstOrNull { it.type == "meta" }
      if (meta != null) {
        val metaStart = meta.offset + meta.headerSize + 4
        val metaEnd = meta.offset + meta.size
        val metaBoxes = parseBoxes(buf, metaStart, metaEnd)
        val ilst = metaBoxes.firstOrNull { it.type == "ilst" }
        if (ilst != null) {
          val ilstItems = parseBoxes(buf, ilst.offset + ilst.headerSize, ilst.offset + ilst.size)
          for (item in ilstItems) {
            val dataBoxes = parseBoxes(buf, item.offset + item.headerSize, item.offset + item.size)
            val dataBox = dataBoxes.firstOrNull { it.type == "data" } ?: continue
            val dataPayloadOffset = dataBox.offset + dataBox.headerSize + 8
            val dataPayloadLen = (dataBox.size - dataBox.headerSize - 8)
            if (dataPayloadLen > 0 && dataPayloadOffset + dataPayloadLen <= buf.size) {
              when (item.type) {
                "\u00a9nam" ->
                  title = String(buf, dataPayloadOffset, dataPayloadLen, Charsets.UTF_8).trim()
                "\u00a9ART",
                "aART" ->
                  if (artist == null) {
                    artist = String(buf, dataPayloadOffset, dataPayloadLen, Charsets.UTF_8).trim()
                  }
                "\u00a9alb" ->
                  album = String(buf, dataPayloadOffset, dataPayloadLen, Charsets.UTF_8).trim()
                "\u00a9day" -> {
                  val dateStr =
                    String(buf, dataPayloadOffset, dataPayloadLen, Charsets.UTF_8).trim()
                  year = dateStr.take(4).toIntOrNull()
                }
                "trkn" -> {
                  if (dataPayloadLen >= 4) {
                    val track =
                      ((buf[dataPayloadOffset + 2].toInt() and 0xFF) shl 8) or
                        (buf[dataPayloadOffset + 3].toInt() and 0xFF)
                    if (track > 0) trackNumber = track
                  }
                }
                "covr" -> {
                  artworkBytes =
                    buf.copyOfRange(dataPayloadOffset, dataPayloadOffset + dataPayloadLen)
                }
              }
            }
          }
        }
      }
    }

    val fallbackBase = path.fileName.toString().substringBeforeLast('.')
    val nameParts = fallbackBase.split(" - ", limit = 2)
    val finalTitle =
      title?.takeIf { it.isNotBlank() }
        ?: (if (nameParts.size == 2) nameParts[0].trim() else fallbackBase)
    val finalArtist =
      artist?.takeIf { it.isNotBlank() } ?: (if (nameParts.size == 2) nameParts[1].trim() else null)

    return LocalAudioFile(
      path = path.toAbsolutePath().toString(),
      title = finalTitle,
      artist = finalArtist,
      album = album?.takeIf { it.isNotBlank() },
      durationMs = durationMs.coerceAtLeast(0L),
      trackNumber = trackNumber,
      year = year,
      artworkBytes = artworkBytes,
    )
  }

  private data class Box(val type: String, val offset: Int, val headerSize: Int, val size: Int)

  private fun parseBoxes(buf: ByteArray, start: Int, end: Int): List<Box> {
    val list = mutableListOf<Box>()
    var pos = start
    while (pos + 8 <= end && pos + 8 <= buf.size) {
      val rawSize = readInt(buf, pos).toLong() and 0xFFFFFFFFL
      val type = String(buf, pos + 4, 4, Charsets.ISO_8859_1)
      val headerSize: Int
      val boxSize: Long =
        when (rawSize) {
          1L -> {
            if (pos + 16 > end || pos + 16 > buf.size) break
            headerSize = 16
            readLong(buf, pos + 8)
          }
          0L -> {
            headerSize = 8
            (end - pos).toLong()
          }
          else -> {
            headerSize = 8
            rawSize
          }
        }
      if (boxSize <= 0 || pos + boxSize > end) break
      list.add(Box(type, pos, headerSize, boxSize.toInt()))
      pos += boxSize.toInt()
    }
    return list
  }

  private fun readInt(b: ByteArray, off: Int): Int =
    ((b[off].toInt() and 0xFF) shl 24) or
      ((b[off + 1].toInt() and 0xFF) shl 16) or
      ((b[off + 2].toInt() and 0xFF) shl 8) or
      (b[off + 3].toInt() and 0xFF)

  private fun readLong(b: ByteArray, off: Int): Long =
    ((readInt(b, off).toLong() and 0xFFFFFFFFL) shl 32) or
      (readInt(b, off + 4).toLong() and 0xFFFFFFFFL)
}
