package echo.music.desktop.ui.theme

import kotlin.test.Test
import kotlin.test.assertNotNull
import org.jetbrains.skia.RuntimeEffect

class ShadersTest {

  @Test
  fun meshGradientAgslCompiles() {
    val effect = compileOrSkipSkiko(MESH_GRADIENT_AGSL)
    assertNotNull(effect)
  }

  @Test
  fun auroraAgslCompiles() {
    val effect = compileOrSkipSkiko(AURORA_AGSL)
    assertNotNull(effect)
  }

  private fun compileOrSkipSkiko(source: String): RuntimeEffect? =
    try {
      RuntimeEffect.makeForShader(source)
    } catch (error: LinkageError) {
      null
    }
}
