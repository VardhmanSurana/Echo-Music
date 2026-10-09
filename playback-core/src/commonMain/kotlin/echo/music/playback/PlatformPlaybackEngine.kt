package echo.music.playback

expect object PlatformPlaybackEngineFactory {
  fun create(type: EngineType): PlaybackEngine
}
