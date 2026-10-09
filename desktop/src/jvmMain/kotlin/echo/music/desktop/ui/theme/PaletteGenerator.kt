package echo.music.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

object PaletteGenerator {

  private const val MAX_CACHE_ENTRIES = 16

  private const val BUCKET_BITS = 4

  private const val DEDUPE_DISTANCE_THRESHOLD = 96

  private const val MAX_COLORS = 8

  private val httpClient by lazy {
    HttpClient(OkHttp) {
      install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 5_000
        socketTimeoutMillis = 10_000
      }
    }
  }

  private val cache = ConcurrentHashMap<String, List<Color>>()

  private val seedColors =
    listOf(
      Color(0xFF2B6CB0),
      Color(0xFF805AD5),
      Color(0xFF319795),
      Color(0xFFDD6B20),
      Color(0xFFD53F8C),
    )

  val defaultPalette: List<Color> = buildImmersivePalette(seedColors)

  suspend fun paletteFor(artworkUrl: String?): List<Color> {
    if (artworkUrl.isNullOrBlank()) return defaultPalette
    cache[artworkUrl]?.let {
      if (cache.size > MAX_CACHE_ENTRIES) trimCache(artworkUrl)
      return it
    }
    val palette = runCatching {
      val bytes = httpClient.get(artworkUrl).body<ByteArray>()
      buildImmersivePalette(extractPalette(bytes))
    }
      .getOrDefault(defaultPalette)
    cache[artworkUrl] = palette
    trimCache(artworkUrl)
    return palette
  }

  private fun trimCache(retain: String) {
    while (cache.size > MAX_CACHE_ENTRIES) {
      val eldest = cache.keys.firstOrNull { it != retain } ?: break
      cache.remove(eldest)
    }
  }

  suspend fun extractPalette(imageBytes: ByteArray, maxSize: Int = 48): List<Color> =
    withContext(Dispatchers.IO) {
      val image = Image.makeFromEncoded(imageBytes)
      val scale = min(1.0, maxSize.toDouble() / max(image.width, image.height))
      val width = max(1, (image.width * scale).roundToInt())
      val height = max(1, (image.height * scale).roundToInt())
      val bitmap =
        Bitmap().apply {
          allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
        }
      val surface =
        Surface.makeRaster(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL))
      try {
        surface.canvas.drawImageRect(
          image,
          Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
          Rect.makeWH(width.toFloat(), height.toFloat()),
          SamplingMode.MITCHELL,
          null,
          false,
        )
        surface.makeImageSnapshot().readPixels(bitmap, 0, 0)
      } finally {
        surface.close()
      }
      histogram(bitmap)
    }

  private fun histogram(bitmap: Bitmap): List<Color> {
    val buckets = HashMap<Int, LongArray>()
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        val packed = bitmap.getColor(x, y)
        if ((packed ushr 24) < 128) continue
        val red = (packed shr 16) and 0xFF
        val green = (packed shr 8) and 0xFF
        val blue = packed and 0xFF
        val key =
          ((red shr BUCKET_BITS) shl 8) or ((green shr BUCKET_BITS) shl 4) or (blue shr BUCKET_BITS)
        val bucket = buckets.getOrPut(key) { LongArray(4) }
        bucket[0] += 1
        bucket[1] += red.toLong()
        bucket[2] += green.toLong()
        bucket[3] += blue.toLong()
      }
    }
    return buckets.values
      .map { bucket ->
        val count = bucket[0].coerceAtLeast(1)
        Color(
          (bucket[1] / count).toInt().coerceIn(0, 255),
          (bucket[2] / count).toInt().coerceIn(0, 255),
          (bucket[3] / count).toInt().coerceIn(0, 255),
        ) to bucket[0]
      }
      .sortedByDescending { it.second }
      .map { it.first }
      .fold(mutableListOf<Color>()) { kept, color ->
        val isDuplicate = kept.any { existing ->
          val dr = (existing.red - color.red) * 255f
          val dg = (existing.green - color.green) * 255f
          val db = (existing.blue - color.blue) * 255f
          dr * dr + dg * dg + db * db <
            DEDUPE_DISTANCE_THRESHOLD.toFloat() * DEDUPE_DISTANCE_THRESHOLD
        }
        if (!isDuplicate) kept.add(color)
        kept
      }
      .take(MAX_COLORS)
  }

  fun complementary(color: Color): Color {
    val hsl = rgbToHsl(color)
    return hslToRgb((hsl[0] + 0.5f) % 1f, hsl[1], hsl[2], color.alpha)
  }

  fun buildImmersivePalette(colors: List<Color>): List<Color> {
    val source = colors.filter { it.alpha > 0f }
    val fallback = defaultPaletteFallback()
    val dominant = source.getOrElse(0) { fallback[0] }
    val accent1 = source.getOrElse(1) { fallback[1] }
    val accent2 = source.getOrElse(2) { lerp(dominant, accent1, 0.5f) }
    val complement = complementary(dominant)
    val mix1 = lerp(dominant, accent1, 0.5f)
    val mix2 = lerp(accent2, complement, 0.5f)
    return listOf(dominant, accent1, accent2, complement, mix1, mix2)
  }

  private fun defaultPaletteFallback(): List<Color> = seedColors

  private fun rgbToHsl(color: Color): FloatArray {
    val red = color.red
    val green = color.green
    val blue = color.blue
    val maxChannel = max(red, max(green, blue))
    val minChannel = min(red, min(green, blue))
    val lightness = (maxChannel + minChannel) / 2f
    val delta = maxChannel - minChannel
    if (delta <= 1e-6f) return floatArrayOf(0f, 0f, lightness)
    val saturation =
      if (lightness > 0.5f) delta / (2f - maxChannel - minChannel)
      else delta / (maxChannel + minChannel)
    val hue =
      when (maxChannel) {
        red -> ((green - blue) / delta + (if (green < blue) 6f else 0f)) / 6f
        green -> ((blue - red) / delta + 2f) / 6f
        else -> ((red - green) / delta + 4f) / 6f
      }
    return floatArrayOf(hue, saturation, lightness)
  }

  private fun hslToRgb(hue: Float, saturation: Float, lightness: Float, alpha: Float): Color {
    if (saturation <= 1e-6f) {
      val channel = lightness
      return Color(channel, channel, channel, alpha)
    }
    val q =
      if (lightness < 0.5f) lightness * (1f + saturation)
      else lightness + saturation - lightness * saturation
    val p = 2f * lightness - q
    fun channel(t: Float): Float {
      var value = t
      if (value < 0f) value += 1f
      if (value > 1f) value -= 1f
      return when {
        value < 1f / 6f -> p + (q - p) * 6f * value
        value < 1f / 2f -> q
        value < 2f / 3f -> p + (q - p) * (2f / 3f - value) * 6f
        else -> p
      }
    }
    return Color(
      channel(hue + 1f / 3f).coerceIn(0f, 1f),
      channel(hue).coerceIn(0f, 1f),
      channel(hue - 1f / 3f).coerceIn(0f, 1f),
      alpha,
    )
  }
}

private fun Color(red: Int, green: Int, blue: Int): Color =
  Color(0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue)
