package echo.music.playback

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface MpvLibrary : Library {
  fun mpv_create(): Pointer?

  fun mpv_initialize(ctx: Pointer): Int

  fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int

  fun mpv_command(ctx: Pointer, args: Array<String>): Int

  fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int

  fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?

  fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int

  fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer

  fun mpv_request_event(ctx: Pointer, eventId: Int, enable: Int): Int

  fun mpv_free(ptr: Pointer?)

  fun mpv_terminate_destroy(ctx: Pointer)

  fun mpv_error_string(error: Int): Pointer?
}

class MpvPlaybackEngine : PlaybackEngine {

  private val lib: MpvLibrary
  private val ctx: Pointer

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

  private var eventThread: Thread? = null

  init {
    var createdLibrary: MpvLibrary? = null
    var createdContext: Pointer? = null
    try {
      val library = loadMpvLibrary()
      createdLibrary = library
      val context =
        library.mpv_create() ?: throw EngineUnavailableException("mpv_create returned null")
      createdContext = context
      val initResult = library.mpv_initialize(context)
      if (initResult < 0) {
        throw EngineUnavailableException(
          "mpv_initialize failed: ${library.errorString(initResult)}"
        )
      }
      library.mpv_set_option_string(context, "no-video", "")
      library.mpv_set_option_string(context, "input-default-bindings", "no")
      library.mpv_set_option_string(context, "input-vo-keyboard", "no")
      library.mpv_set_option_string(context, "osc", "no")
      library.mpv_set_option_string(context, "pause", "yes")
      library.mpv_observe_property(context, USERDATA_TIME_POS, "time-pos", FORMAT_DOUBLE)
      library.mpv_observe_property(context, USERDATA_DURATION, "duration", FORMAT_DOUBLE)
      library.mpv_observe_property(context, USERDATA_PAUSE, "pause", FORMAT_FLAG)
      lib = library
      ctx = context
    } catch (t: Throwable) {
      if (createdContext != null) {
        try {
          createdLibrary?.mpv_terminate_destroy(createdContext)
        } catch (_: Throwable) {}
      }
      throw if (t is EngineUnavailableException) t
      else EngineUnavailableException("libmpv is unavailable", t)
    }
    eventThread =
      Thread({ eventLoop() }, "mpv-event-loop").apply {
        isDaemon = true
        start()
      }
  }

  override suspend fun loadStream(
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long,
  ) {
    load(url, headers, startPositionMs)
  }

  override suspend fun loadFile(path: String, startPositionMs: Long) {
    load(path, emptyMap(), startPositionMs)
  }

  override fun play() {
    paused = false
    setProperty("pause", "no")
  }

  override fun pause() {
    paused = true
    setProperty("pause", "yes")
  }

  override fun seekTo(positionMs: Long) {
    _currentPosition.value = positionMs
    setProperty("time-pos", (positionMs / 1000.0).toString())
  }

  override fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _volume.value = clamped
    setProperty("volume", (clamped * 100f).toString())
  }

  override fun setOutputDevice(device: AudioOutputDevice) {
    setProperty("audio-device", device.id)
  }

  override fun release() {
    if (released) return
    released = true
    try {
      eventThread?.join(2000)
    } catch (_: InterruptedException) {}
    try {
      lib.mpv_terminate_destroy(ctx)
    } catch (_: Throwable) {}
    _state.value = PlaybackState.Stopped
  }

  private fun load(url: String, headers: Map<String, String>, startPositionMs: Long) {
    if (released) {
      _state.value = PlaybackState.Failed("Engine has been released")
      return
    }
    _state.value = PlaybackState.Loading
    _currentPosition.value = startPositionMs
    _duration.value = 0L
    pendingStartMs = startPositionMs
    val args = mutableListOf("loadfile", url, "replace")
    if (headers.isNotEmpty()) {
      val headerFields = headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
      args += "http-header-fields=$headerFields"
    }
    val result = lib.mpv_command(ctx, args.toTypedArray())
    if (result < 0) {
      _state.value = PlaybackState.Failed(lib.errorString(result))
    }
  }

  private fun eventLoop() {
    while (!released) {
      val event =
        try {
          lib.mpv_wait_event(ctx, EVENT_WAIT_TIMEOUT_SECONDS)
        } catch (_: Throwable) {
          break
        }
      val eventId = event.getInt(EVENT_ID_OFFSET)
      if (eventId == EVENT_NONE) {
        continue
      }
      when (eventId) {
        EVENT_FILE_LOADED -> onFileLoaded()
        EVENT_END_FILE -> onEndFile(event.getPointer(EVENT_DATA_OFFSET))
        EVENT_PLAYBACK_RESTART -> {
          if (!paused) {
            _state.value = PlaybackState.Playing
          }
        }
        EVENT_PROPERTY_CHANGE -> onPropertyChange(event.getPointer(EVENT_DATA_OFFSET))
        else -> Unit
      }
    }
  }

  private fun onFileLoaded() {
    val durationPointer = lib.mpv_get_property_string(ctx, "duration")
    if (durationPointer != null) {
      try {
        _duration.value =
          (durationPointer.getString(0).toDoubleOrNull() ?: 0.0).let {
            (it * 1000).toLong()
          }
      } finally {
        lib.mpv_free(durationPointer)
      }
    }
    val start = pendingStartMs
    pendingStartMs = 0L
    if (start > 0) {
      setProperty("time-pos", (start / 1000.0).toString())
    }
    _state.value = if (paused) PlaybackState.Paused else PlaybackState.Playing
  }

  private fun onEndFile(endFileData: Pointer?) {
    if (endFileData == null) {
      return
    }
    val reason = endFileData.getInt(0)
    val error = endFileData.getInt(4)
    when {
      reason == END_FILE_REASON_ERROR ->
        _state.value = PlaybackState.Failed(lib.errorString(if (error != 0) error else -1))
      reason == END_FILE_REASON_EOF -> _state.value = PlaybackState.Stopped
      else -> Unit
    }
  }

  private fun onPropertyChange(propertyData: Pointer?) {
    if (propertyData == null) {
      return
    }
    val namePointer = propertyData.getPointer(0)
    val name = namePointer?.getString(0) ?: return
    val format = propertyData.getInt(FORMAT_OFFSET)
    val valuePointer = propertyData.getPointer(VALUE_OFFSET)
    when (name) {
      "time-pos" -> {
        if (format == FORMAT_DOUBLE && valuePointer != null) {
          _currentPosition.value = (valuePointer.getDouble(0) * 1000).toLong()
        }
      }
      "duration" -> {
        if (format == FORMAT_DOUBLE && valuePointer != null) {
          _duration.value = (valuePointer.getDouble(0) * 1000).toLong()
        }
      }
      "pause" -> {
        if (format == FORMAT_FLAG && valuePointer != null) {
          paused = valuePointer.getInt(0) != 0
          _state.value = if (paused) PlaybackState.Paused else PlaybackState.Playing
        }
      }
      else -> Unit
    }
  }

  private fun setProperty(name: String, value: String) {
    if (released) {
      return
    }
    try {
      lib.mpv_set_property_string(ctx, name, value)
    } catch (_: Throwable) {}
  }

  private fun MpvLibrary.errorString(code: Int): String {
    val pointer =
      try {
        mpv_error_string(code)
      } catch (_: Throwable) {
        null
      }
    return pointer?.getString(0) ?: "mpv error $code"
  }

  companion object {
    private val LIBRARY_CANDIDATES = listOf("mpv", "libmpv.so.2", "libmpv.so.1")

    private const val EVENT_NONE = 0
    private const val EVENT_END_FILE = 7
    private const val EVENT_FILE_LOADED = 8
    private const val EVENT_PLAYBACK_RESTART = 21
    private const val EVENT_PROPERTY_CHANGE = 22

    private const val END_FILE_REASON_EOF = 0
    private const val END_FILE_REASON_ERROR = 3

    private const val FORMAT_DOUBLE = 5
    private const val FORMAT_FLAG = 3

    private const val EVENT_ID_OFFSET = 0L
    private const val EVENT_DATA_OFFSET = 16L
    private const val FORMAT_OFFSET = 8L
    private const val VALUE_OFFSET = 16L

    private const val USERDATA_TIME_POS = 1L
    private const val USERDATA_DURATION = 2L
    private const val USERDATA_PAUSE = 3L

    private const val EVENT_WAIT_TIMEOUT_SECONDS = 0.2

    private var loadedLibrary: MpvLibrary? = null

    private fun loadMpvLibrary(): MpvLibrary {
      loadedLibrary?.let {
        return it
      }
      var lastFailure: Throwable? = null
      for (name in LIBRARY_CANDIDATES) {
        try {
          val library = Native.load(name, MpvLibrary::class.java)
          loadedLibrary = library
          return library
        } catch (t: Throwable) {
          lastFailure = t
        }
      }
      throw EngineUnavailableException("Could not load libmpv", lastFailure)
    }
  }
}
