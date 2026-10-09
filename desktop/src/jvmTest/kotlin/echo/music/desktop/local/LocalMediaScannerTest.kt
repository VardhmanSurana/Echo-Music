package echo.music.desktop.local

import echo.music.playback.PlaybackMediaItem
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey

class LocalMediaScannerTest {
  private lateinit var root: Path

  @BeforeTest
  fun setUp() {
    root = Files.createTempDirectory("echo-scanner-test")
  }

  @AfterTest
  fun tearDown() {
    root.toFile().deleteRecursively()
  }

  @Test
  fun filtersNonAudioFilesAndMatchesExtensionsCaseInsensitively() {
    writeWav(root.resolve("song.wav"))
    writeWav(root.resolve("UPPER.WAV"))
    Files.writeString(root.resolve("notes.txt"), "not audio")
    Files.write(root.resolve("cover.jpg"), byteArrayOf(0x01, 0x02))

    val result = scan(root.toString())

    assertEquals(setOf("song", "UPPER"), result.files.map { it.title }.toSet())
    assertTrue(result.errors.isEmpty(), "errors: ${result.errors}")
  }

  @Test
  fun traversesRecursivelyAndSkipsHiddenDirsAndFiles() {
    writeWav(root.resolve("a").resolve("one.wav"))
    writeWav(root.resolve("b").resolve("c").resolve("two.wav"))
    writeWav(root.resolve(".hidden").resolve("hidden.wav"))
    writeWav(root.resolve(".dotfile.wav"))

    val result = scan(root.toString())

    assertEquals(listOf("one", "two"), result.files.map { it.title }.sorted())
    assertTrue(result.scannedDirs.none { it.contains(".hidden") })
    assertTrue(result.errors.isEmpty(), "errors: ${result.errors}")
  }

  @Test
  fun corruptFileIsRecordedInErrorsAndScanCompletes() {
    writeWav(root.resolve("good.wav"))
    Files.write(root.resolve("broken.mp3"), byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7))

    val result = scan(root.toString())

    assertEquals(listOf("good"), result.files.map { it.title })
    assertEquals(1, result.errors.size)
    assertTrue(result.errors.first().contains("broken.mp3"), "errors: ${result.errors}")
  }

  @Test
  fun extractsTagsAndFallsBackToFileName() {
    val tagged = writeWav(root.resolve("fallback-name.wav"))
    val audioFile = AudioFileIO.read(tagged.toFile())
    audioFile.tag.setField(FieldKey.TITLE, "Tagged Title")
    audioFile.tag.setField(FieldKey.ARTIST, "Tagged Artist")
    audioFile.tag.setField(FieldKey.ALBUM, "Tagged Album")
    AudioFileIO.write(audioFile)
    writeWav(root.resolve("untagged.wav"))

    val result = scan(root.toString())

    val taggedFile = result.files.first { it.title == "Tagged Title" }
    assertEquals("Tagged Artist", taggedFile.artist)
    assertEquals("Tagged Album", taggedFile.album)
    val untaggedFile = result.files.first { it.path.endsWith("untagged.wav") }
    assertEquals("untagged", untaggedFile.title)
  }

  @Test
  fun durationIsPositiveForGeneratedWav() {
    writeWav(root.resolve("long.wav"), seconds = 3)

    val result = scan(root.toString())

    val file = result.files.single()
    assertTrue(file.durationMs in 2000L..4000L, "durationMs=${file.durationMs}")
  }

  @Test
  fun toPlaybackItemsMapsToLocalFileSources() {
    val wav = writeWav(root.resolve("mapped.wav"))

    val scanner = LocalMediaScanner(initialRoots = listOf(root.toString()))
    scanner.rescan()
    runBlocking { scanner.isScanning.first { !it } }
    val items = scanner.toPlaybackItems()

    val item = items.single()
    assertEquals(wav.toAbsolutePath().toString(), item.id)
    assertEquals(PlaybackMediaItem.Source.LocalFile(wav.toAbsolutePath().toString()), item.source)
    assertEquals("mapped", item.title)
    assertTrue(item.durationMs > 0L)
  }

  @Test
  fun addAndRemoveRootUpdateWatchedRoots() {
    val scanner = LocalMediaScanner()
    assertTrue(scanner.roots.value.isEmpty())

    scanner.addRoot(root.toString())
    assertEquals(listOf(root.toString()), scanner.roots.value)

    scanner.addRoot(root.toString())
    assertEquals(listOf(root.toString()), scanner.roots.value)

    scanner.removeRoot(root.toString())
    assertTrue(scanner.roots.value.isEmpty())
  }

  private fun scan(vararg roots: String): ScanResult {
    val scanner = LocalMediaScanner(initialRoots = roots.toList())
    scanner.rescan()
    runBlocking { scanner.isScanning.first { !it } }
    return scanner.result.value
  }

  private fun writeWav(path: Path, seconds: Int = 1): Path {
    Files.createDirectories(path.parent)
    val format = AudioFormat(44100f, 16, 1, true, false)
    val data = ByteArray(44100 * 2 * seconds)
    AudioInputStream(ByteArrayInputStream(data), format, data.size / 2L).use { input ->
      AudioSystem.write(input, AudioFileFormat.Type.WAVE, path.toFile())
    }
    return path
  }
}
