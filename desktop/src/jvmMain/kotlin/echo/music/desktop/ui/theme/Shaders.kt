package echo.music.desktop.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeShader
import echo.music.desktop.system.RenderBudget
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

val MESH_GRADIENT_AGSL: String =
  """
  uniform float2 uResolution;
  uniform float uTime;
  uniform half4 uColor0;
  uniform half4 uColor1;
  uniform half4 uColor2;
  uniform half4 uColor3;
  uniform half4 uColor4;
  uniform half4 uColor5;
  uniform float uIntensity;

  float blobWeight(float2 uv, float2 center, float radius) {
    float2 d = uv - center;
    float r = max(radius, 0.0001);
    return exp(-dot(d, d) / (r * r));
  }

  half4 main(float2 coord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float aspect = res.x / res.y;
    float2 uv = float2(coord.x / res.x * aspect, coord.y / res.y);
    float t = uTime;

    float2 c0 = float2(aspect * 0.5 + 0.42 * sin(t * 0.17), 0.5 + 0.38 * cos(t * 0.13));
    float2 c1 = float2(aspect * 0.5 + 0.40 * cos(t * 0.11 + 1.7), 0.5 + 0.40 * sin(t * 0.19 + 0.6));
    float2 c2 = float2(aspect * 0.5 + 0.45 * sin(t * 0.09 + 3.1), 0.5 + 0.35 * cos(t * 0.15 + 2.2));
    float2 c3 = float2(aspect * 0.5 + 0.38 * cos(t * 0.23 + 4.4), 0.5 + 0.42 * sin(t * 0.12 + 5.0));
    float2 c4 = float2(aspect * 0.5 + 0.44 * sin(t * 0.14 + 5.6), 0.5 + 0.36 * cos(t * 0.21 + 0.9));
    float2 c5 = float2(aspect * 0.5 + 0.40 * cos(t * 0.16 + 2.8), 0.5 + 0.40 * sin(t * 0.10 + 3.7));

    float w0 = blobWeight(uv, c0, 0.50);
    float w1 = blobWeight(uv, c1, 0.46);
    float w2 = blobWeight(uv, c2, 0.42);
    float w3 = blobWeight(uv, c3, 0.48);
    float w4 = blobWeight(uv, c4, 0.44);
    float w5 = blobWeight(uv, c5, 0.40);

    float wsum = max(w0 + w1 + w2 + w3 + w4 + w5, 0.0001);
    float3 col = (float3(uColor0.rgb) * w0 + float3(uColor1.rgb) * w1 + float3(uColor2.rgb) * w2 +
        float3(uColor3.rgb) * w3 + float3(uColor4.rgb) * w4 + float3(uColor5.rgb) * w5) / wsum;
    col = clamp(col * uIntensity, 0.0, 1.0);
    return half4(half3(col), 1.0);
  }
  """
    .trimIndent()

val AURORA_AGSL: String =
  """
  uniform float2 uResolution;
  uniform float uTime;
  uniform half4 uColor0;
  uniform half4 uColor1;
  uniform half4 uColor2;
  uniform half4 uColor3;
  uniform half4 uColor4;
  uniform half4 uColor5;
  uniform float uIntensity;

  float bandWeight(float y, float center, float width) {
    float d = y - center;
    float w = max(width, 0.0001);
    return exp(-d * d / (w * w));
  }

  half4 main(float2 coord) {
    float2 res = max(uResolution, float2(1.0, 1.0));
    float2 uv = coord / res;
    float t = uTime * 0.6;

    float y0 = 0.30 + 0.10 * sin(uv.x * 2.6 + t * 0.5);
    float y1 = 0.55 + 0.12 * cos(uv.x * 2.1 - t * 0.4 + 1.3);
    float y2 = 0.42 + 0.11 * sin(uv.x * 3.0 + t * 0.3 + 2.6);
    float y3 = 0.68 + 0.09 * cos(uv.x * 1.7 + t * 0.6 + 4.1);

    float w0 = bandWeight(uv.y, y0, 0.16);
    float w1 = bandWeight(uv.y, y1, 0.18);
    float w2 = bandWeight(uv.y, y2, 0.14);
    float w3 = bandWeight(uv.y, y3, 0.15);
    float base = 0.10;

    float wsum = max(w0 + w1 + w2 + w3 + base, 0.0001);
    float3 col = (float3(uColor0.rgb) * w0 + float3(uColor1.rgb) * w1 + float3(uColor2.rgb) * w2 +
        float3(uColor3.rgb) * w3 + float3(uColor4.rgb) * base) / wsum;
    col = mix(col, float3(uColor5.rgb), 0.15);
    col = clamp(col * uIntensity, 0.0, 1.0);
    return half4(half3(col), 1.0);
  }
  """
    .trimIndent()

private const val SHADER_TIME_SCALE = 0.6f

private const val SHADER_COLOR_SLOTS = 6

private val meshGradientEffect: RuntimeEffect by lazy {
  RuntimeEffect.makeForShader(MESH_GRADIENT_AGSL)
}

private val auroraEffect: RuntimeEffect by lazy { RuntimeEffect.makeForShader(AURORA_AGSL) }

fun meshGradientBrush(
  colors: List<Color>,
  timeSeconds: Float,
  intensity: Float,
  widthPx: Float = 0f,
  heightPx: Float = 0f,
): ShaderBrush =
  buildShaderBrush(meshGradientEffect, colors, timeSeconds, intensity, widthPx, heightPx)

fun auroraGradientBrush(
  colors: List<Color>,
  timeSeconds: Float,
  intensity: Float,
  widthPx: Float = 0f,
  heightPx: Float = 0f,
): ShaderBrush = buildShaderBrush(auroraEffect, colors, timeSeconds, intensity, widthPx, heightPx)

private fun buildShaderBrush(
  effect: RuntimeEffect,
  colors: List<Color>,
  timeSeconds: Float,
  intensity: Float,
  widthPx: Float,
  heightPx: Float,
): ShaderBrush {
  val builder = RuntimeShaderBuilder(effect)
  builder.uniform(
    "uResolution",
    widthPx.takeIf { it > 0f } ?: 1f,
    heightPx.takeIf { it > 0f } ?: 1f,
  )
  builder.uniform("uTime", timeSeconds * SHADER_TIME_SCALE)
  for (index in 0 until SHADER_COLOR_SLOTS) {
    val color = colors.getOrElse(index) { colors.lastOrNull() ?: Color.Black }
    builder.uniform("uColor$index", color.red, color.green, color.blue, 1f)
  }
  builder.uniform("uIntensity", intensity)
  return ShaderBrush(builder.makeShader().asComposeShader())
}

@Composable
fun rememberAnimatedShaderTime(): MutableFloatState {
  val timeState = remember { mutableFloatStateOf(0f) }
  val budget = LocalRenderBudget.current
  LaunchedEffect(budget) {
    if (budget != RenderBudget.FULL) return@LaunchedEffect
    var lastFrameNanos = -1L
    while (true) {
      val now = withFrameNanos { it }
      if (lastFrameNanos >= 0) {
        timeState.floatValue += (now - lastFrameNanos) / 1_000_000_000f
      }
      lastFrameNanos = now
    }
  }
  return timeState
}
