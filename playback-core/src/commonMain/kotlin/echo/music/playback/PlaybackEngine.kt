package echo.music.playback

import kotlinx.coroutines.flow.StateFlow

interface PlaybackEngine {
  val state: StateFlow<PlaybackState>

  val currentPosition: StateFlow<Long>

  val duration: StateFlow<Long>

  val volume: StateFlow<Float>

  suspend fun loadStream(url: String, headers: Map<String, String>, startPositionMs: Long = 0)

  suspend fun loadFile(path: String, startPositionMs: Long = 0)

  fun play()

  fun pause()

  fun seekTo(positionMs: Long)

  fun setVolume(volume: Float)

  fun setOutputDevice(device: AudioOutputDevice)

  fun release()
}

enum class EngineType {
  AUTO,
  MPV,
  GSTREAMER,
  FALLBACK,
}

data class AudioOutputDevice(
  val id: String,
  val name: String,
  val isDefault: Boolean = false,
)

class EngineUnavailableException(
  message: String? = null,
  cause: Throwable? = null,
) : Exception(message, cause)
