package echo.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakePlaybackEngine(val type: EngineType = EngineType.FALLBACK) : PlaybackEngine {

  val calls = mutableListOf<String>()

  var loadedUrl: String? = null
    private set

  var loadedHeaders: Map<String, String> = emptyMap()
    private set

  var loadedPath: String? = null
    private set

  var loadedStartPositionMs: Long = 0L
    private set

  var lastSeekMs: Long? = null
    private set

  var lastVolume: Float? = null
    private set

  var lastDevice: AudioOutputDevice? = null
    private set

  var released: Boolean = false
    private set

  private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
  override val state: StateFlow<PlaybackState> = _state.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

  private val _duration = MutableStateFlow(0L)
  override val duration: StateFlow<Long> = _duration.asStateFlow()

  private val _volume = MutableStateFlow(1.0f)
  override val volume: StateFlow<Float> = _volume.asStateFlow()

  override suspend fun loadStream(
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long,
  ) {
    calls += "loadStream:$url"
    loadedUrl = url
    loadedHeaders = headers
    loadedStartPositionMs = startPositionMs
    _currentPosition.value = startPositionMs
    _state.value = PlaybackState.Loading
  }

  override suspend fun loadFile(path: String, startPositionMs: Long) {
    calls += "loadFile:$path"
    loadedPath = path
    loadedStartPositionMs = startPositionMs
    _currentPosition.value = startPositionMs
    _state.value = PlaybackState.Loading
  }

  override fun play() {
    calls += "play"
    _state.value = PlaybackState.Playing
  }

  override fun pause() {
    calls += "pause"
    _state.value = PlaybackState.Paused
  }

  override fun seekTo(positionMs: Long) {
    calls += "seekTo:$positionMs"
    lastSeekMs = positionMs
    _currentPosition.value = positionMs
  }

  override fun setVolume(volume: Float) {
    calls += "setVolume:$volume"
    lastVolume = volume
    _volume.value = volume
  }

  override fun setOutputDevice(device: AudioOutputDevice) {
    calls += "setOutputDevice:${device.id}"
    lastDevice = device
  }

  override fun release() {
    calls += "release"
    released = true
    _state.value = PlaybackState.Stopped
  }

  fun emitState(newState: PlaybackState) {
    _state.value = newState
  }

  fun setPosition(positionMs: Long) {
    _currentPosition.value = positionMs
  }

  fun setDuration(durationMs: Long) {
    _duration.value = durationMs
  }
}
