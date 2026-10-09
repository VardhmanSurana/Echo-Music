package echo.music.desktop.lyrics

data class LyricWord(val text: String, val startMs: Long, val endMs: Long?)

data class LyricsLine(
  val startMs: Long,
  val endMs: Long?,
  val text: String,
  val words: List<LyricWord>?,
)

data class LyricsResult(
  val timed: List<LyricsLine>?,
  val plain: String?,
  val provider: String,
  val isInstrumental: Boolean = false,
)
