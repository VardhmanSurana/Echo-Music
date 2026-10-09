package echo.music.desktop.lyrics

object LrcParser {

  private val metadataRegex = Regex("""^\[(ti|ar|al|by|offset|length|re|ve|au|tool):[^]]*]$""")

  private val lineTimestampRegex = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

  private val inlineTimestampRegex = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")

  private val wordBlockRegex = Regex("""^<([^<>]+)>$""")

  private val wordEntryRegex = Regex("""^(.+):(\d+(?:\.\d+)?):(\d+(?:\.\d+)?)$""")

  private val braceMarkerRegex = Regex("""\{[^}]*}""")

  private class Accumulator(var startMs: Long, var text: String, var words: List<LyricWord>?)

  fun parseLrc(raw: String): List<LyricsLine> {
    val entries = mutableListOf<Accumulator>()
    for (rawLine in raw.lines()) {
      val trimmed = rawLine.trim()
      if (trimmed.isEmpty() || metadataRegex.matches(trimmed)) continue
      var rest = trimmed
      val startTimes = mutableListOf<Long>()
      while (true) {
        val match = lineTimestampRegex.find(rest) ?: break
        startTimes.add(timestampToMs(match))
        rest = rest.substring(match.range.last + 1)
      }
      val clean = braceMarkerRegex.replace(rest, "")
      if (startTimes.isEmpty()) {
        val block = parseWordBlock(clean)
        if (block != null) entries.lastOrNull()?.words = block
        continue
      }
      for (startTime in startTimes) {
        val (text, words) = parseInline(clean, startTime)
        entries.add(Accumulator(startTime, text, words))
      }
    }
    val sorted = entries.sortedBy { it.startMs }
    return sorted.mapIndexed { index, entry ->
      LyricsLine(
        startMs = entry.startMs,
        endMs = sorted.getOrNull(index + 1)?.startMs,
        text = entry.text,
        words = entry.words,
      )
    }
  }

  fun currentLineIndex(lines: List<LyricsLine>, positionMs: Long): Int {
    if (lines.isEmpty() || positionMs < lines.first().startMs) return -1
    var low = 0
    var high = lines.lastIndex
    var best = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (lines[mid].startMs <= positionMs) {
        best = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return best
  }

  fun currentWordIndex(line: LyricsLine, positionMs: Long): Int {
    val words = line.words?.takeIf { it.isNotEmpty() } ?: return -1
    if (positionMs < words.first().startMs) return -1
    var low = 0
    var high = words.lastIndex
    var best = 0
    while (low <= high) {
      val mid = (low + high) ushr 1
      if (words[mid].startMs <= positionMs) {
        best = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return best
  }

  fun approximateWordFraction(line: LyricsLine, positionMs: Long): Float {
    val endMs = line.endMs ?: return 1f
    val duration = endMs - line.startMs
    if (duration <= 0L) return 1f
    return ((positionMs - line.startMs).toFloat() / duration).coerceIn(0f, 1f)
  }

  private fun timestampToMs(match: MatchResult): Long {
    val minutes = match.groupValues[1].toLong()
    val seconds = match.groupValues[2].toLongOrNull() ?: 0L
    val fraction = match.groupValues[3]
    val fractionMs =
      when (fraction.length) {
        0 -> 0L
        1 -> (fraction.toLongOrNull() ?: 0L) * 100L
        2 -> (fraction.toLongOrNull() ?: 0L) * 10L
        else -> fraction.take(3).toLongOrNull() ?: 0L
      }
    return minutes * 60_000L + seconds * 1_000L + fractionMs
  }

  private fun parseInline(clean: String, lineStartMs: Long): Pair<String, List<LyricWord>?> {
    val tags = inlineTimestampRegex.findAll(clean).toList()
    if (tags.isEmpty()) return clean.trim() to null
    val text = StringBuilder()
    val words = mutableListOf<LyricWord>()
    val prefix = clean.substring(0, tags.first().range.first)
    text.append(prefix)
    if (prefix.isNotBlank()) {
      words.add(LyricWord(prefix.trim(), lineStartMs, null))
    }
    for (index in tags.indices) {
      val tag = tags[index]
      val contentStart = tag.range.last + 1
      val contentEnd = tags.getOrNull(index + 1)?.range?.first ?: clean.length
      val wordText = clean.substring(contentStart, contentEnd)
      text.append(wordText)
      if (wordText.isNotBlank()) {
        words.add(LyricWord(wordText.trim(), timestampToMs(tag), null))
      }
    }
    return text.toString().trim() to words.ifEmpty { null }
  }

  private fun parseWordBlock(clean: String): List<LyricWord>? {
    val match = wordBlockRegex.find(clean.trim()) ?: return null
    val entries = match.groupValues[1].split("|")
    val words = entries.mapNotNull { entry ->
      val wordMatch = wordEntryRegex.find(entry.trim()) ?: return null
      val seconds = wordMatch.groupValues[2].toDoubleOrNull() ?: return null
      val endSeconds = wordMatch.groupValues[3].toDoubleOrNull() ?: return null
      LyricWord(
        text = wordMatch.groupValues[1],
        startMs = (seconds * 1000).toLong(),
        endMs = (endSeconds * 1000).toLong(),
      )
    }
    return words.ifEmpty { null }
  }
}
