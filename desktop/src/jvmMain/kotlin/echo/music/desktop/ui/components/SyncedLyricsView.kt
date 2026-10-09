package echo.music.desktop.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import echo.music.desktop.lyrics.DesktopLyricsResolver
import echo.music.desktop.lyrics.LrcParser
import echo.music.desktop.lyrics.LyricsLine
import echo.music.desktop.lyrics.LyricsResult
import echo.music.desktop.system.RenderBudget
import echo.music.desktop.ui.theme.LocalRenderBudget
import echo.music.playback.PlaybackManager
import kotlin.math.abs

@Composable
fun SyncedLyricsView(
  resolver: DesktopLyricsResolver,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  autoScroll: Boolean = true,
) {
  val result by resolver.current.collectAsState()
  val loading by resolver.loading.collectAsState()
  SyncedLyricsView(
    result = result,
    modifier = modifier,
    compact = compact,
    autoScroll = autoScroll,
    loading = loading,
  )
}

@Composable
fun SyncedLyricsView(
  result: LyricsResult?,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  autoScroll: Boolean = true,
  loading: Boolean = false,
  positionMs: Long? = null,
) {
  val managerPosition by PlaybackManager.position.collectAsState()
  val position = positionMs ?: managerPosition
  val timedLines = result?.timed
  val plainText = result?.plain
  when {
    result?.isInstrumental == true -> InstrumentalState(compact, modifier)
    timedLines != null -> TimedLyrics(timedLines, position, compact, autoScroll, modifier)
    plainText != null -> PlainLyrics(plainText, modifier)
    loading -> LoadingSkeleton(compact, modifier)
    else -> EmptyLyricsState(compact, modifier)
  }
}

@Composable
private fun TimedLyrics(
  lines: List<LyricsLine>,
  positionMs: Long,
  compact: Boolean,
  autoScroll: Boolean,
  modifier: Modifier,
) {
  val budget = LocalRenderBudget.current
  val listState = rememberLazyListState()
  val activeIndex = LrcParser.currentLineIndex(lines, positionMs)
  LaunchedEffect(activeIndex, autoScroll, budget) {
    if (!autoScroll || activeIndex < 0) return@LaunchedEffect
    if (budget == RenderBudget.FULL) {
      listState.animateScrollToItem(activeIndex)
    } else {
      listState.scrollToItem(activeIndex)
    }
  }
  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val halfPadding = maxHeight / 2
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      contentPadding = PaddingValues(vertical = halfPadding),
    ) {
      items(lines.size) { index ->
        LyricLineItem(
          line = lines[index],
          isActive = index == activeIndex,
          distance = index - activeIndex,
          positionMs = positionMs,
          compact = compact,
          budget = budget,
        )
      }
    }
  }
}

@Composable
private fun LyricLineItem(
  line: LyricsLine,
  isActive: Boolean,
  distance: Int,
  positionMs: Long,
  compact: Boolean,
  budget: RenderBudget,
) {
  val contentColor = MaterialTheme.colorScheme.onSurface
  val highlightColor = MaterialTheme.colorScheme.primary
  val absDistance = abs(distance)
  val alpha =
    when {
      isActive -> 1f
      absDistance == 1 -> 0.35f
      absDistance == 2 -> 0.25f
      else -> 0.18f
    }
  val fontSize =
    when {
      isActive && !compact -> 30.sp
      isActive -> 19.sp
      !compact -> (22 - absDistance.coerceAtMost(3)).sp
      else -> 15.sp
    }
  val blurRadius: Dp =
    if (!isActive && absDistance in 1..3 && budget == RenderBudget.FULL) {
      (absDistance * 2).dp
    } else {
      0.dp
    }
  Text(
    text = buildLineAnnotatedString(line, isActive, positionMs, contentColor, highlightColor),
    fontSize = fontSize,
    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
    color = contentColor.copy(alpha = alpha),
    textAlign = TextAlign.Center,
    modifier =
      Modifier.fillMaxWidth()
        .let { if (blurRadius > 0.dp) it.blur(blurRadius) else it }
        .clickable { PlaybackManager.seekTo(line.startMs) }
        .padding(horizontal = if (compact) 12.dp else 48.dp, vertical = 6.dp),
  )
}

private fun buildLineAnnotatedString(
  line: LyricsLine,
  isActive: Boolean,
  positionMs: Long,
  contentColor: Color,
  highlightColor: Color,
): AnnotatedString {
  if (!isActive) return AnnotatedString(line.text)
  val words = line.words?.takeIf { it.isNotEmpty() }
  if (words != null) {
    val currentIndex = LrcParser.currentWordIndex(line, positionMs)
    return buildAnnotatedString {
      words.forEachIndexed { index, word ->
        val isCurrent = index == currentIndex
        withStyle(
          SpanStyle(
            color = if (isCurrent) highlightColor else contentColor.copy(alpha = 0.65f),
            fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
          )
        ) {
          append(word.text)
        }
        val next = words.getOrNull(index + 1)
        val needsSpace =
          next != null &&
            !word.text.endsWith(" ") &&
            !word.text.endsWith("\n") &&
            !next.text.startsWith(" ")
        if (needsSpace) append(" ")
      }
    }
  }
  return approximateHighlightedText(line, positionMs, contentColor, highlightColor)
}

private fun approximateHighlightedText(
  line: LyricsLine,
  positionMs: Long,
  contentColor: Color,
  highlightColor: Color,
): AnnotatedString {
  val text = line.text
  if (text.isBlank()) return AnnotatedString(text)
  val tokens = text.trim().split(whitespaceRegex)
  val totalWeight = tokens.sumOf { it.length }.coerceAtLeast(1)
  val fraction = LrcParser.approximateWordFraction(line, positionMs)
  var current = 0
  var consumed = 0
  tokens.forEachIndexed { index, token ->
    consumed += token.length
    if (consumed.toDouble() / totalWeight <= fraction + 1e-6) current = index
  }
  return buildAnnotatedString {
    tokens.forEachIndexed { index, token ->
      val style =
        if (index <= current) {
          SpanStyle(
            color = if (index == current) highlightColor else contentColor.copy(alpha = 0.75f),
            fontWeight = if (index == current) FontWeight.ExtraBold else FontWeight.Bold,
          )
        } else {
          SpanStyle(color = contentColor.copy(alpha = 0.65f), fontWeight = FontWeight.Bold)
        }
      withStyle(style) { append(token) }
      if (index != tokens.lastIndex) append(" ")
    }
  }
}

@Composable
private fun PlainLyrics(plain: String, modifier: Modifier) {
  Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
    plain.lines().forEach { line ->
      Text(
        text = line,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      )
    }
  }
}

@Composable
private fun InstrumentalState(compact: Boolean, modifier: Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "♪ Instrumental ♪",
      fontSize = if (compact) 16.sp else 26.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
    )
  }
}

@Composable
private fun EmptyLyricsState(compact: Boolean, modifier: Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Text(
      text = "No lyrics found",
      style =
        if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
  }
}

@Composable
private fun LoadingSkeleton(compact: Boolean, modifier: Modifier) {
  val budget = LocalRenderBudget.current
  val transition = rememberInfiniteTransition(label = "lyricsSkeleton")
  val animatedAlpha by
    transition.animateFloat(
      initialValue = 0.15f,
      targetValue = 0.4f,
      animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
      label = "lyricsSkeletonAlpha",
    )
  val alpha = if (budget == RenderBudget.FULL) animatedAlpha else 0.25f
  val skeletonColor = MaterialTheme.colorScheme.onSurface
  val lineWidths = listOf(0.9f, 0.65f, 0.8f, 0.55f, 0.85f, 0.7f)
  Column(
    modifier = modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 64.dp),
    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    val height = if (compact) 14.dp else 20.dp
    lineWidths.forEach { widthFraction ->
      Box(
        modifier =
          Modifier.fillMaxWidth(widthFraction)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(skeletonColor.copy(alpha = alpha))
      )
    }
  }
}

private val whitespaceRegex = Regex("""\s+""")
