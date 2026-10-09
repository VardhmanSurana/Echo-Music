package echo.music.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object PlaybackManager {

  private val lock = Any()

  private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
  val state: StateFlow<PlaybackState> = _state.asStateFlow()

  private val _currentItem = MutableStateFlow<PlaybackMediaItem?>(null)
  val currentItem: StateFlow<PlaybackMediaItem?> = _currentItem.asStateFlow()

  private val _position = MutableStateFlow(0L)
  val position: StateFlow<Long> = _position.asStateFlow()

  private val _duration = MutableStateFlow(0L)
  val duration: StateFlow<Long> = _duration.asStateFlow()

  private val _volume = MutableStateFlow(1.0f)
  val volume: StateFlow<Float> = _volume.asStateFlow()

  private val _activeEngine = MutableStateFlow(EngineType.AUTO)
  val activeEngine: StateFlow<EngineType> = _activeEngine.asStateFlow()

  private val queueManager = QueueManager()
  val queue: StateFlow<List<PlaybackMediaItem>>
    get() = queueManager.queue

  val currentIndex: StateFlow<Int>
    get() = queueManager.currentIndex

  internal var engineFactory: (EngineType) -> PlaybackEngine = { type ->
    PlatformPlaybackEngineFactory.create(type)
  }

  internal var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private var engine: PlaybackEngine? = null
  private var bridgeJob: Job? = null

  @Volatile private var forwardEngineState = true

  fun playItem(item: PlaybackMediaItem) {
    scope.launch {
      synchronized(lock) {
        queueManager.setQueue(listOf(item), 0)
      }
      startItem(item, startPositionMs = 0, playWhenReady = true)
    }
  }

  fun playQueue(items: List<PlaybackMediaItem>, startIndex: Int) {
    scope.launch {
      synchronized(lock) {
        queueManager.setQueue(items, startIndex)
      }
      val item = synchronized(lock) { queueManager.currentItem } ?: return@launch
      startItem(item, startPositionMs = 0, playWhenReady = true)
    }
  }

  fun play() {
    engine?.play()
  }

  fun pause() {
    engine?.pause()
  }

  fun togglePlay() {
    when (_state.value) {
      PlaybackState.Playing -> pause()
      PlaybackState.Paused,
      PlaybackState.Stopped,
      is PlaybackState.Buffering -> play()
      else -> Unit
    }
  }

  fun seekTo(positionMs: Long) {
    _position.value = positionMs
    engine?.seekTo(positionMs)
  }

  fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _volume.value = clamped
    engine?.setVolume(clamped)
  }

  fun next() {
    scope.launch { navigate(synchronized(lock) { queueManager.playNext() }) }
  }

  fun previous() {
    scope.launch { navigate(synchronized(lock) { queueManager.playPrevious() }) }
  }

  fun jumpTo(index: Int) {
    scope.launch {
      val item =
        synchronized(lock) {
          queueManager.jumpTo(index)
          queueManager.currentItem
        } ?: return@launch
      startItem(item, startPositionMs = 0, playWhenReady = true)
    }
  }

  fun addNext(item: PlaybackMediaItem) {
    synchronized(lock) { queueManager.addNext(item) }
  }

  fun addToQueue(item: PlaybackMediaItem) {
    synchronized(lock) { queueManager.addToQueue(item) }
  }

  fun removeAt(index: Int) {
    synchronized(lock) { queueManager.removeAt(index) }
  }

  fun move(from: Int, to: Int) {
    synchronized(lock) { queueManager.move(from, to) }
  }

  fun setShuffle(enabled: Boolean) {
    synchronized(lock) { queueManager.setShuffle(enabled) }
  }

  fun setRepeatMode(mode: RepeatMode) {
    synchronized(lock) { queueManager.setRepeatMode(mode) }
  }

  fun setEngine(type: EngineType) {
    synchronized(lock) {
      val old = engine
      val item = _currentItem.value
      val positionMs = _position.value
      val wasPaused =
        _state.value == PlaybackState.Paused || old?.state?.value == PlaybackState.Paused
      bridgeJob?.cancel()
      bridgeJob = null
      engine = null
      if (old != null) {
        try {
          old.release()
        } catch (_: Throwable) {}
      }
      val resolved = createEngineResolving(type)
      if (resolved == null) {
        _state.value = PlaybackState.Failed("No playback engine available")
        return
      }
      attachEngine(resolved.first, resolved.second)
      if (item != null) {
        scope.launch {
          startItem(item, positionMs, playWhenReady = !wasPaused)
        }
      } else {
        _state.value = PlaybackState.Idle
      }
    }
  }

  internal fun reset() {
    synchronized(lock) {
      bridgeJob?.cancel()
      bridgeJob = null
      val old = engine
      engine = null
      if (old != null) {
        try {
          old.release()
        } catch (_: Throwable) {}
      }
      queueManager.setQueue(emptyList(), 0)
      queueManager.setShuffle(false)
      queueManager.setRepeatMode(RepeatMode.OFF)
      _state.value = PlaybackState.Idle
      _currentItem.value = null
      _position.value = 0L
      _duration.value = 0L
      _volume.value = 1.0f
      _activeEngine.value = EngineType.AUTO
      engineFactory = { type -> PlatformPlaybackEngineFactory.create(type) }
      scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
  }

  private suspend fun navigate(item: PlaybackMediaItem?) {
    if (item == null) {
      forwardEngineState = false
      engine?.pause()
      _state.value = PlaybackState.Stopped
      return
    }
    startItem(item, startPositionMs = 0, playWhenReady = true)
  }

  private suspend fun startItem(
    item: PlaybackMediaItem,
    startPositionMs: Long,
    playWhenReady: Boolean,
  ) {
    val target =
      synchronized(lock) { ensureEngineLocked() }
        ?: run {
          _state.value = PlaybackState.Failed("No playback engine available")
          return
        }
    _currentItem.value = item
    _position.value = startPositionMs
    _duration.value = item.durationMs
    forwardEngineState = true
    _state.value = PlaybackState.Loading
    try {
      when (val source = item.source) {
        is PlaybackMediaItem.Source.StreamUrl ->
          target.loadStream(source.url, source.headers, startPositionMs)
        is PlaybackMediaItem.Source.LocalFile -> target.loadFile(source.path, startPositionMs)
      }
      if (playWhenReady) {
        target.play()
      } else {
        target.pause()
      }
    } catch (t: Throwable) {
      _state.value = PlaybackState.Failed(t.message ?: "Playback failed")
    }
  }

  private fun ensureEngineLocked(): PlaybackEngine? {
    engine?.let {
      return it
    }
    val resolved = createEngineResolving(EngineType.AUTO) ?: return null
    attachEngine(resolved.first, resolved.second)
    return resolved.first
  }

  private fun createEngineResolving(type: EngineType): Pair<PlaybackEngine, EngineType>? {
    val order =
      when (type) {
        EngineType.AUTO,
        EngineType.MPV -> listOf(EngineType.MPV, EngineType.GSTREAMER, EngineType.FALLBACK)
        EngineType.GSTREAMER -> listOf(EngineType.GSTREAMER, EngineType.FALLBACK)
        EngineType.FALLBACK -> listOf(EngineType.FALLBACK)
      }
    for (candidate in order) {
      val created =
        try {
          engineFactory(candidate)
        } catch (_: Throwable) {
          null
        }
      if (created != null) {
        return created to candidate
      }
    }
    return null
  }

  private fun attachEngine(newEngine: PlaybackEngine, type: EngineType) {
    engine = newEngine
    _activeEngine.value = type
    forwardEngineState = true
    bridgeJob = scope.launch {
      launch {
        newEngine.state.collect { newState ->
          if (forwardEngineState) {
            _state.value = newState
          }
        }
      }
      launch {
        newEngine.currentPosition.collect { positionMs ->
          if (newEngine.state.value == PlaybackState.Playing) {
            _position.value = positionMs
          }
        }
      }
      launch {
        newEngine.duration.collect { durationMs ->
          if (durationMs > 0) {
            _duration.value = durationMs
          }
        }
      }
    }
    newEngine.setVolume(_volume.value)
  }
}
