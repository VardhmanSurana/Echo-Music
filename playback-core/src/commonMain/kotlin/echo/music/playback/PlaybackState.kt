package echo.music.playback

sealed interface PlaybackState {
  data object Idle : PlaybackState

  data object Loading : PlaybackState

  data class Buffering(val progress: Float?) : PlaybackState

  data object Playing : PlaybackState

  data object Paused : PlaybackState

  data object Stopped : PlaybackState

  data class Failed(val message: String) : PlaybackState
}
