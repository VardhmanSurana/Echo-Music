package echo.music.playback

import java.io.File
import java.net.URI
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.freedesktop.gstreamer.Bus
import org.freedesktop.gstreamer.Format
import org.freedesktop.gstreamer.Gst
import org.freedesktop.gstreamer.State
import org.freedesktop.gstreamer.StateChangeReturn
import org.freedesktop.gstreamer.elements.PlayBin
import org.freedesktop.gstreamer.elements.PlayFlags
import org.freedesktop.gstreamer.event.SeekFlags
import org.freedesktop.gstreamer.event.SeekType

class GStreamerPlaybackEngine : PlaybackEngine {

  private val playbin: PlayBin

  private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
  override val state: StateFlow<PlaybackState> = _state.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

  private val _duration = MutableStateFlow(0L)
  override val duration: StateFlow<Long> = _duration.asStateFlow()

  private val _volume = MutableStateFlow(1.0f)
  override val volume: StateFlow<Float> = _volume.asStateFlow()

  @Volatile private var released = false

  @Volatile private var paused = true

  @Volatile private var pendingStartMs = 0L

  private var pollerThread: Thread? = null

  private val eosListener = Bus.EOS { onEos() }

  private val errorListener = Bus.ERROR { _, _, message ->
    if (!released) {
      _state.value = PlaybackState.Failed(message ?: "GStreamer playback error")
    }
  }

  private val bufferingListener = Bus.BUFFERING { _, percent ->
    if (!released) {
      _state.value =
        if (percent in 0..99) PlaybackState.Buffering(percent / 100f)
        else if (paused) PlaybackState.Paused else PlaybackState.Playing
    }
  }

  private val stateChangedListener = Bus.STATE_CHANGED { source, _, newState, _ ->
    if (!released && source === playbin) {
      when (newState) {
        State.PLAYING -> {
          paused = false
          _state.value = PlaybackState.Playing
        }
        State.PAUSED -> {
          paused = true
          _state.value = PlaybackState.Paused
        }
        else -> Unit
      }
    }
  }

  init {
    try {
      Gst.init("echo-music-playback")
      val bin = PlayBin("echo-music-playbin")
      playbin = bin
      bin.setFlags(bin.flags - PlayFlags.VIDEO)
      val bus = bin.bus ?: throw EngineUnavailableException("GStreamer bus is unavailable")
      bus.connect(eosListener)
      bus.connect(errorListener)
      bus.connect(bufferingListener)
      bus.connect(stateChangedListener)
    } catch (t: Throwable) {
      throw if (t is EngineUnavailableException) t
      else EngineUnavailableException("GStreamer is unavailable", t)
    }
    pollerThread =
      Thread({ pollLoop() }, "gstreamer-poll").apply {
        isDaemon = true
        start()
      }
  }

  override suspend fun loadStream(
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long,
  ) {
    load(url, startPositionMs)
  }

  override suspend fun loadFile(path: String, startPositionMs: Long) {
    load(File(path).toURI().toString(), startPositionMs)
  }

  override fun play() {
    if (released) return
    paused = false
    val pending = pendingStartMs
    pendingStartMs = 0L
    if (pending > 0) {
      seekInternal(pending)
    }
    val result = playbin.setState(State.PLAYING)
    if (result == StateChangeReturn.FAILURE) {
      _state.value = PlaybackState.Failed("GStreamer could not start playback")
    } else {
      _state.value = PlaybackState.Playing
    }
  }

  override fun pause() {
    if (released) return
    paused = true
    playbin.setState(State.PAUSED)
    _state.value = PlaybackState.Paused
  }

  override fun seekTo(positionMs: Long) {
    if (released) return
    _currentPosition.value = positionMs
    seekInternal(positionMs)
  }

  override fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _volume.value = clamped
    if (released) return
    try {
      playbin.setVolume(clamped.toDouble())
    } catch (_: Throwable) {}
  }

  override fun setOutputDevice(device: AudioOutputDevice) {
    if (released) return
    try {
      playbin.setState(playbin.state)
    } catch (_: Throwable) {}
  }

  override fun release() {
    if (released) return
    released = true
    try {
      playbin.setState(State.NULL)
    } catch (_: Throwable) {}
    pollerThread?.interrupt()
    try {
      playbin.dispose()
    } catch (_: Throwable) {}
    _state.value = PlaybackState.Stopped
  }

  private fun load(uri: String, startPositionMs: Long) {
    if (released) {
      _state.value = PlaybackState.Failed("Engine has been released")
      return
    }
    _state.value = PlaybackState.Loading
    _currentPosition.value = startPositionMs
    _duration.value = 0L
    pendingStartMs = startPositionMs
    paused = true
    try {
      playbin.setURI(URI(uri))
      val result = playbin.setState(State.READY)
      if (result == StateChangeReturn.FAILURE) {
        _state.value = PlaybackState.Failed("GStreamer could not load $uri")
      }
    } catch (t: Throwable) {
      _state.value = PlaybackState.Failed(t.message ?: "GStreamer could not load $uri")
    }
  }

  private fun onEos() {
    if (!released) {
      _state.value = PlaybackState.Stopped
    }
  }

  private fun seekInternal(positionMs: Long) {
    try {
      playbin.seek(
        1.0,
        Format.TIME,
        EnumSet.of(SeekFlags.FLUSH, SeekFlags.KEY_UNIT),
        SeekType.SET,
        TimeUnit.MILLISECONDS.toNanos(positionMs),
        SeekType.NONE,
        -1L,
      )
    } catch (_: Throwable) {}
  }

  private fun pollLoop() {
    while (!released) {
      try {
        val position = playbin.queryPosition(Format.TIME)
        if (position >= 0) {
          _currentPosition.value = TimeUnit.NANOSECONDS.toMillis(position)
        }
        val duration = playbin.queryDuration(Format.TIME)
        if (duration > 0) {
          _duration.value = TimeUnit.NANOSECONDS.toMillis(duration)
        }
      } catch (_: Throwable) {}
      try {
        Thread.sleep(POSITION_POLL_INTERVAL_MS)
      } catch (_: InterruptedException) {
        return
      }
    }
  }

  companion object {
    private const val POSITION_POLL_INTERVAL_MS = 250L
  }
}
