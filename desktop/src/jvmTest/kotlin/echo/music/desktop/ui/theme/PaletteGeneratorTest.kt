package echo.music.desktop.ui.theme

import androidx.compose.ui.graphics.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PaletteGeneratorTest {

  private fun pngBytes(width: Int, height: Int, colorAt: (Int, Int) -> Int): ByteArray {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until height) {
      for (x in 0 until width) {
        image.setRGB(x, y, colorAt(x, y))
      }
    }
    val output = ByteArrayOutputStream()
    ImageIO.write(image, "png", output)
    return output.toByteArray()
  }

  @Test
  fun extractsDominantHueFamily() = runBlocking {
    val bytes =
      pngBytes(16, 16) { x, _ ->
        if (x < 12) 0xFFCC2020.toInt() else 0xFF2030C0.toInt()
      }
    val palette = PaletteGenerator.extractPalette(bytes)
    assertTrue(palette.isNotEmpty())
    val dominant = palette.first()
    assertTrue(dominant.red > 0.6f, "expected red-dominant palette, got $palette")
    assertTrue(dominant.blue < 0.4f, "expected red-dominant palette, got $palette")
  }

  @Test
  fun extractsMultipleDistinctColors() = runBlocking {
    val bytes =
      pngBytes(16, 16) { x, y ->
        when {
          x < 8 && y < 8 -> 0xFFDD0000.toInt()
          x >= 8 && y < 8 -> 0xFF00DD00.toInt()
          x < 8 -> 0xFF0000DD.toInt()
          else -> 0xFFDDDD00.toInt()
        }
      }
    val palette = PaletteGenerator.extractPalette(bytes)
    assertTrue(palette.size >= 3, "expected at least 3 distinct colors, got $palette")
  }

  @Test
  fun dedupesNearIdenticalColors() = runBlocking {
    val bytes =
      pngBytes(8, 8) { x, y ->
        if ((x + y) % 2 == 0) 0xFFC81E1E.toInt() else 0xFFC81F1F.toInt()
      }
    val palette = PaletteGenerator.extractPalette(bytes)
    assertEquals(1, palette.size)
  }

  @Test
  fun complementaryAdds180Hue() {
    val complement = PaletteGenerator.complementary(Color.Red)
    assertTrue(abs(complement.red) < 0.05f, "expected no red, got $complement")
    assertTrue(complement.green > 0.95f, "expected full green, got $complement")
    assertTrue(complement.blue > 0.95f, "expected full blue, got $complement")
  }

  @Test
  fun complementaryRoundTripsHue() {
    val base = Color(0xFF805AD5)
    val complement = PaletteGenerator.complementary(base)
    val roundTrip = PaletteGenerator.complementary(complement)
    assertTrue(abs(roundTrip.red - base.red) < 0.02f)
    assertTrue(abs(roundTrip.green - base.green) < 0.02f)
    assertTrue(abs(roundTrip.blue - base.blue) < 0.02f)
  }

  @Test
  fun buildImmersivePaletteReturnsSixOrderedColors() {
    val palette = PaletteGenerator.buildImmersivePalette(listOf(Color.Red, Color.Green, Color.Blue))
    assertEquals(6, palette.size)
    assertEquals(Color.Red, palette[0])
    assertEquals(Color.Green, palette[1])
    assertEquals(Color.Blue, palette[2])
    assertEquals(PaletteGenerator.complementary(Color.Red), palette[3])
  }

  @Test
  fun buildImmersivePalettePadsWithFallbacksForEmptyInput() {
    val palette = PaletteGenerator.buildImmersivePalette(emptyList())
    assertEquals(6, palette.size)
  }

  @Test
  fun defaultPaletteIsDeterministic() {
    assertEquals(PaletteGenerator.defaultPalette, PaletteGenerator.defaultPalette)
    assertEquals(6, PaletteGenerator.defaultPalette.size)
  }
}
