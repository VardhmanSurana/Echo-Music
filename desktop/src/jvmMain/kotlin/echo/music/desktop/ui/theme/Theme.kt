package echo.music.desktop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.rememberDynamicColorScheme
import echo.music.desktop.system.RenderBudget
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}

object ThemeSettings {
  val mode: MutableStateFlow<ThemeMode> = MutableStateFlow(ThemeMode.SYSTEM)
  val isPureBlack: MutableStateFlow<Boolean> = MutableStateFlow(false)
  val seedColor: MutableStateFlow<Color> = MutableStateFlow(BrandColor)

  fun setMode(newMode: ThemeMode) {
    mode.value = newMode
  }

  fun setPureBlack(enabled: Boolean) {
    isPureBlack.value = enabled
  }

  fun setSeedColor(color: Color) {
    seedColor.value = color
  }
}

val LocalRenderBudget = staticCompositionLocalOf { RenderBudget.FULL }

val BrandColor = Color(0xFF1E88E5)

@Composable
fun Theme(content: @Composable () -> Unit) {
  val mode = ThemeSettings.mode.collectAsState().value
  val pureBlack = ThemeSettings.isPureBlack.collectAsState().value
  val seed = ThemeSettings.seedColor.collectAsState().value

  val dark =
    when (mode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }

  val colorScheme =
    rememberDynamicColorScheme(
      seedColor = seed,
      isDark = dark,
      isAmoled = dark && pureBlack,
    )
  MaterialTheme(colorScheme = colorScheme, content = content)
}
