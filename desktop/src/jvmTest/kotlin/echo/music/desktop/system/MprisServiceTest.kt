package echo.music.desktop.system

import echo.music.playback.PlaybackMediaItem
import echo.music.playback.PlaybackState
import echo.music.playback.RepeatMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.freedesktop.dbus.ObjectPath

class MprisServiceTest {

  @Test
  fun playbackStateMapsToMprisStatus() {
    assertEquals("Playing", MprisMapper.playbackStatus(PlaybackState.Playing))
    assertEquals("Playing", MprisMapper.playbackStatus(PlaybackState.Loading))
    assertEquals("Playing", MprisMapper.playbackStatus(PlaybackState.Buffering(null)))
    assertEquals("Paused", MprisMapper.playbackStatus(PlaybackState.Paused))
    assertEquals("Stopped", MprisMapper.playbackStatus(PlaybackState.Idle))
    assertEquals("Stopped", MprisMapper.playbackStatus(PlaybackState.Stopped))
    assertEquals("Stopped", MprisMapper.playbackStatus(PlaybackState.Failed("boom")))
  }

  @Test
  fun trackIdProducesValidObjectPathForWeirdIds() {
    val ids = listOf("abc123", "we/ird id", "héllo wörld", "emoji😀track", "", "a-b.c:d*e", "_x_")
    for (id in ids) {
      val path = MprisMapper.trackIdPath(id)
      assertTrue(
        path.startsWith("${MprisMapper.TRACK_ID_PREFIX}/"),
        "path '$path' missing prefix for id '$id'",
      )
      val element = path.removePrefix("${MprisMapper.TRACK_ID_PREFIX}/")
      assertTrue(element.isNotEmpty(), "empty path element for id '$id'")
      assertTrue(
        element.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' },
        "invalid object path element '$element' for id '$id'",
      )
    }
  }

  @Test
  fun metadataContainsMprisFields() {
    val item =
      PlaybackMediaItem(
        id = "we/ird id",
        title = "Title",
        artist = "Artist",
        album = "Album",
        artworkUrl = "https://example.com/art.jpg",
        durationMs = 205_000L,
        source = PlaybackMediaItem.Source.StreamUrl("https://example.com/stream"),
      )
    val metadata = MprisMapper.metadata(item)
    val trackId = metadata["mpris:trackid"] as ObjectPath
    assertEquals("${MprisMapper.TRACK_ID_PREFIX}/we_ird_id", trackId.path)
    assertEquals(205_000_000L, metadata["mpris:length"])
    assertEquals("Title", metadata["xesam:title"])
    assertEquals(arrayListOf("Artist"), metadata["xesam:artist"])
    assertEquals("Album", metadata["xesam:album"])
    assertEquals("https://example.com/art.jpg", metadata["mpris:artUrl"])
  }

  @Test
  fun metadataOmitsOptionalFieldsWhenMissing() {
    val item =
      PlaybackMediaItem(
        id = "id",
        title = "Title",
        source = PlaybackMediaItem.Source.LocalFile("/tmp/a.mp3"),
      )
    val metadata = MprisMapper.metadata(item)
    assertFalse(metadata.containsKey("mpris:artUrl"))
    assertFalse(metadata.containsKey("xesam:artist"))
    assertFalse(metadata.containsKey("xesam:album"))
    assertFalse(metadata.containsKey("mpris:length"))
    assertEquals("Title", metadata["xesam:title"])
  }

  @Test
  fun metadataForNullItemIsEmpty() {
    assertTrue(MprisMapper.metadata(null).isEmpty())
  }

  @Test
  fun loopStatusConversionsRoundTrip() {
    assertEquals("None", MprisMapper.loopStatusOf(RepeatMode.OFF))
    assertEquals("Track", MprisMapper.loopStatusOf(RepeatMode.ONE))
    assertEquals("Playlist", MprisMapper.loopStatusOf(RepeatMode.ALL))
    assertEquals(RepeatMode.OFF, MprisMapper.repeatModeOf("None"))
    assertEquals(RepeatMode.ONE, MprisMapper.repeatModeOf("Track"))
    assertEquals(RepeatMode.ALL, MprisMapper.repeatModeOf("Playlist"))
    assertEquals(RepeatMode.OFF, MprisMapper.repeatModeOf("bogus"))
    for (mode in RepeatMode.entries) {
      assertEquals(mode, MprisMapper.repeatModeOf(MprisMapper.loopStatusOf(mode)))
    }
  }

  @Test
  fun shuffleConversionsBothDirections() {
    assertTrue(MprisMapper.shuffleToMpris(true))
    assertFalse(MprisMapper.shuffleToMpris(false))
    assertTrue(MprisMapper.shuffleFromMpris(true))
    assertFalse(MprisMapper.shuffleFromMpris(false))
  }

  @Test
  fun volumeConversionsClamp() {
    assertEquals(1.0, MprisMapper.volumeOf(2.0f))
    assertEquals(0.0, MprisMapper.volumeOf(-1.0f))
    assertEquals(0.42, MprisMapper.volumeOf(0.42f), 1e-6)
    assertEquals(0f, MprisMapper.volumeLevel(-3.0))
    assertEquals(1f, MprisMapper.volumeLevel(5.0))
    assertEquals(0.5f, MprisMapper.volumeLevel(0.5))
  }

  @Test
  fun durationConversionsToMicros() {
    assertEquals(1_000_000L, MprisMapper.msToMicros(1_000L))
    assertEquals(1_500L, MprisMapper.microsToMs(1_500_000L))
    assertEquals(0L, MprisMapper.microsToMs(0L))
  }
}
