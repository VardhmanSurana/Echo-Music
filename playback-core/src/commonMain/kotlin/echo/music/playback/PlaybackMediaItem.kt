package echo.music.playback

data class PlaybackMediaItem(
  val id: String,
  val title: String,
  val artist: String = "",
  val album: String = "",
  val artworkUrl: String? = null,
  val durationMs: Long = 0L,
  val source: Source,
) {
  sealed interface Source {
    data class StreamUrl(
      val url: String,
      val headers: Map<String, String> = emptyMap(),
    ) : Source

    data class LocalFile(val path: String) : Source
  }
}
