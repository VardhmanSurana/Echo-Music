package echo.music.playback

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Before
import org.junit.Test

class PlaybackManagerTest {

  private lateinit var fake: FakePlaybackEngine
  private lateinit var testScope: CoroutineScope

  @Before
  fun setUp() {
    PlaybackManager.reset()
    fake = FakePlaybackEngine()
    testScope = CoroutineScope(UnconfinedTestDispatcher())
    PlaybackManager.scope = testScope
    PlaybackManager.engineFactory = { fake }
  }

  @After
  fun tearDown() {
    PlaybackManager.reset()
  }

  private fun media(id: String, source: PlaybackMediaItem.Source? = null) =
    PlaybackMediaItem(
      id = id,
      title = "Title $id",
      artist = "Artist",
      album = "Album",
      artworkUrl = null,
      durationMs = 180_000L,
      source = source ?: PlaybackMediaItem.Source.LocalFile("/music/$id.mp3"),
    )

  @Test
  fun playItemTransitionsThroughStates() {
    assertEquals(PlaybackState.Idle, PlaybackManager.state.value)
    assertNull(PlaybackManager.currentItem.value)

    val states = mutableListOf<PlaybackState>()
    val collector = PlaybackManager.state.onEach { states += it }.launchIn(testScope)

    val item = media("one")
    PlaybackManager.playItem(item)

    assertEquals(item, PlaybackManager.currentItem.value)
    assertEquals(listOf("loadFile:/music/one.mp3", "play"), fake.calls.takeLast(2))
    assertEquals(PlaybackState.Playing, PlaybackManager.state.value)
    assertTrue(PlaybackState.Loading in states)

    PlaybackManager.pause()
    assertEquals(PlaybackState.Paused, PlaybackManager.state.value)

    PlaybackManager.togglePlay()
    assertEquals(PlaybackState.Playing, PlaybackManager.state.value)

    PlaybackManager.togglePlay()
    assertEquals(PlaybackState.Paused, PlaybackManager.state.value)

    fake.emitState(PlaybackState.Stopped)
    assertEquals(PlaybackState.Stopped, PlaybackManager.state.value)

    collector.cancel()
  }

  @Test
  fun playStreamItemForwardsHeadersAndDuration() {
    val headers = mapOf("User-Agent" to "EchoMusic", "Referer" to "https://example.com")
    val item = media("stream", PlaybackMediaItem.Source.StreamUrl("https://cdn/x.m4a", headers))
    PlaybackManager.playItem(item)

    assertEquals("https://cdn/x.m4a", fake.loadedUrl)
    assertEquals(headers, fake.loadedHeaders)
    assertEquals(180_000L, PlaybackManager.duration.value)
    assertEquals(EngineType.MPV, PlaybackManager.activeEngine.value)
  }

  @Test
  fun nextAndPreviousNavigateQueueAndStopAtEnd() {
    val items = listOf(media("a"), media("b"), media("c"))
    PlaybackManager.playQueue(items, 1)
    assertEquals(items[1], PlaybackManager.currentItem.value)

    PlaybackManager.next()
    assertEquals(items[2], PlaybackManager.currentItem.value)
    assertEquals("/music/c.mp3", fake.loadedPath)

    PlaybackManager.next()
    assertEquals(items[2], PlaybackManager.currentItem.value)
    assertEquals(PlaybackState.Stopped, PlaybackManager.state.value)

    PlaybackManager.previous()
    assertEquals(items[1], PlaybackManager.currentItem.value)
    assertEquals(PlaybackState.Playing, PlaybackManager.state.value)
  }

  @Test
  fun volumeAndSeekAreForwardedToEngine() {
    PlaybackManager.playItem(media("one"))

    PlaybackManager.setVolume(0.42f)
    assertEquals(0.42f, fake.lastVolume)
    assertEquals(0.42f, PlaybackManager.volume.value)

    PlaybackManager.setVolume(5f)
    assertEquals(1.0f, fake.lastVolume)

    PlaybackManager.seekTo(90_000L)
    assertEquals(90_000L, fake.lastSeekMs)
    assertEquals(90_000L, PlaybackManager.position.value)

    fake.setPosition(95_000L)
    assertEquals(95_000L, PlaybackManager.position.value)

    PlaybackManager.pause()
    fake.setPosition(100_000L)
    assertEquals(95_000L, PlaybackManager.position.value)
  }

  @Test
  fun setEngineReleasesOldEngineAndTransfersState() {
    val first = FakePlaybackEngine()
    PlaybackManager.engineFactory = { first }

    val items = listOf(media("a"), media("b"))
    PlaybackManager.playQueue(items, 0)
    PlaybackManager.pause()
    PlaybackManager.seekTo(55_000L)

    val second = FakePlaybackEngine()
    PlaybackManager.engineFactory = { type ->
      when (type) {
        EngineType.MPV -> throw EngineUnavailableException("libmpv not found")
        EngineType.GSTREAMER -> second
        else -> second
      }
    }

    PlaybackManager.setEngine(EngineType.MPV)

    assertTrue(first.released)
    assertEquals(EngineType.GSTREAMER, PlaybackManager.activeEngine.value)
    assertEquals("/music/a.mp3", second.loadedPath)
    assertEquals(55_000L, second.loadedStartPositionMs)
    assertEquals(items[0], PlaybackManager.currentItem.value)
    assertEquals(PlaybackState.Paused, PlaybackManager.state.value)
    assertEquals(PlaybackState.Paused, second.state.value)
    assertTrue("pause" in second.calls)
  }

  @Test
  fun setEngineFallsBackToLastResortWhenAllButFallbackFail() {
    PlaybackManager.playItem(media("a"))

    val fallback = FakePlaybackEngine()
    PlaybackManager.engineFactory = { type ->
      when (type) {
        EngineType.FALLBACK -> fallback
        else -> throw EngineUnavailableException("$type not available")
      }
    }

    PlaybackManager.setEngine(EngineType.GSTREAMER)

    assertEquals(EngineType.FALLBACK, PlaybackManager.activeEngine.value)
    assertEquals("/music/a.mp3", fallback.loadedPath)
    assertEquals(fake.released, true)
  }

  @Test
  fun setEngineWithNoAvailableEngineReportsFailure() {
    PlaybackManager.playItem(media("a"))
    PlaybackManager.engineFactory = { throw EngineUnavailableException("none") }

    PlaybackManager.setEngine(EngineType.MPV)

    val currentState = PlaybackManager.state.value
    assertTrue(currentState is PlaybackState.Failed, "expected Failed but was $currentState")
    assertTrue(fake.released)
  }

  @Test
  fun initialEngineResolutionFallsBackWhenMpvUnavailable() {
    PlaybackManager.engineFactory = { type ->
      when (type) {
        EngineType.MPV -> throw EngineUnavailableException("no mpv")
        EngineType.GSTREAMER -> throw EngineUnavailableException("no gstreamer")
        else -> fake
      }
    }

    PlaybackManager.playItem(media("a"))

    assertEquals(EngineType.FALLBACK, PlaybackManager.activeEngine.value)
    assertEquals("/music/a.mp3", fake.loadedPath)
  }
}
