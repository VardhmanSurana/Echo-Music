package echo.music.desktop.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LrcParserTest {

  @Test
  fun multipleTimestampsPerLineProduceSeparateEntries() {
    val lines = LrcParser.parseLrc("[00:10.00][00:50.00]chorus line")
    assertEquals(2, lines.size)
    assertEquals(10_000L, lines[0].startMs)
    assertEquals(50_000L, lines[1].startMs)
    assertEquals("chorus line", lines[0].text)
    assertEquals("chorus line", lines[1].text)
  }

  @Test
  fun metadataTagsAndBlankLinesAreSkipped() {
    val raw =
      "[ti:Song Title]\n[ar:Artist]\n\n[al:Album]\n[offset:100]\n[by:uploader]\n[00:05.00]hello"
    val lines = LrcParser.parseLrc(raw)
    assertEquals(1, lines.size)
    assertEquals("hello", lines[0].text)
  }

  @Test
  fun centisecondAndMillisecondPrecision() {
    val lines = LrcParser.parseLrc("[00:01.50]a\n[00:02.250]b\n[01:03.5]c")
    assertEquals(1_500L, lines[0].startMs)
    assertEquals(2_250L, lines[1].startMs)
    assertEquals(63_500L, lines[2].startMs)
  }

  @Test
  fun enhancedInlineWordTimings() {
    val lines = LrcParser.parseLrc("[00:10.00]Hello <00:10.50>beautiful <00:11.20>world")
    assertEquals(1, lines.size)
    assertEquals("Hello beautiful world", lines[0].text)
    val words = lines[0].words
    assertEquals(3, words!!.size)
    assertEquals("Hello", words[0].text)
    assertEquals(10_000L, words[0].startMs)
    assertEquals("beautiful", words[1].text)
    assertEquals(10_500L, words[1].startMs)
    assertEquals("world", words[2].text)
    assertEquals(11_200L, words[2].startMs)
  }

  @Test
  fun betterLyricsWordBlockAttachesToPreviousLine() {
    val raw = "[00:12.00]line text\n<line:12.10:12.50|text:12.50:13.00>"
    val lines = LrcParser.parseLrc(raw)
    assertEquals(1, lines.size)
    assertEquals("line text", lines[0].text)
    val words = lines[0].words
    assertEquals(2, words!!.size)
    assertEquals("line", words[0].text)
    assertEquals(12_100L, words[0].startMs)
    assertEquals(12_500L, words[0].endMs)
    assertEquals("text", words[1].text)
    assertEquals(12_500L, words[1].startMs)
    assertEquals(13_000L, words[1].endMs)
  }

  @Test
  fun braceMarkersAreStripped() {
    val lines = LrcParser.parseLrc("[00:12.34]{agent:A}hello there\n[00:13.00]{bg}oh no")
    assertEquals("hello there", lines[0].text)
    assertEquals("oh no", lines[1].text)
  }

  @Test
  fun endMsDerivedFromNextLineStart() {
    val lines = LrcParser.parseLrc("[00:10.00]one\n[00:20.00]two")
    assertEquals(20_000L, lines[0].endMs)
    assertNull(lines[1].endMs)
  }

  @Test
  fun linesAreSortedByStartTime() {
    val lines = LrcParser.parseLrc("[01:00.00]later\n[00:10.00]earlier")
    assertEquals(10_000L, lines[0].startMs)
    assertEquals(60_000L, lines[1].startMs)
  }

  @Test
  fun currentLineIndexBinarySearch() {
    val lines = LrcParser.parseLrc("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")
    assertEquals(-1, LrcParser.currentLineIndex(lines, 5_000L))
    assertEquals(0, LrcParser.currentLineIndex(lines, 10_000L))
    assertEquals(0, LrcParser.currentLineIndex(lines, 15_000L))
    assertEquals(1, LrcParser.currentLineIndex(lines, 20_000L))
    assertEquals(2, LrcParser.currentLineIndex(lines, 99_000L))
    assertEquals(-1, LrcParser.currentLineIndex(emptyList(), 0L))
  }

  @Test
  fun currentWordIndexTracksWordTimings() {
    val lines = LrcParser.parseLrc("[00:10.00]Hello <00:10.50>beautiful <00:11.20>world")
    val line = lines[0]
    assertEquals(-1, LrcParser.currentWordIndex(line, 9_000L))
    assertEquals(0, LrcParser.currentWordIndex(line, 10_000L))
    assertEquals(1, LrcParser.currentWordIndex(line, 10_600L))
    assertEquals(2, LrcParser.currentWordIndex(line, 11_500L))
  }

  @Test
  fun approximateWordFractionClampsToLineDuration() {
    val lines = LrcParser.parseLrc("[00:10.00]one\n[00:20.00]two")
    val line = lines[0]
    assertTrue(LrcParser.approximateWordFraction(line, 5_000L) <= 0f)
    assertTrue(LrcParser.approximateWordFraction(line, 15_000L) in 0.4f..0.6f)
    assertEquals(1f, LrcParser.approximateWordFraction(line, 25_000L))
    assertEquals(1f, LrcParser.approximateWordFraction(lines[1], 21_000L))
  }

  @Test
  fun plainTextWithoutTimestampsYieldsNoLines() {
    assertTrue(LrcParser.parseLrc("just plain text\nno timestamps here").isEmpty())
  }
}
