package echo.music.playback

actual object PlatformPlaybackEngineFactory {
  actual fun create(type: EngineType): PlaybackEngine {
    try {
      return when (type) {
        EngineType.MPV -> MpvPlaybackEngine()
        EngineType.GSTREAMER -> GStreamerPlaybackEngine()
        EngineType.FALLBACK -> FallbackAudioEngine()
        EngineType.AUTO ->
          throw EngineUnavailableException("AUTO must be resolved to a concrete engine")
      }
    } catch (t: Throwable) {
      throw if (t is EngineUnavailableException) t
      else EngineUnavailableException("Engine $type failed to initialize", t)
    }
  }
}
