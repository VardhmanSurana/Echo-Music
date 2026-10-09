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

  fun setMode(newMode: ThemeMode) {
    mode.value = newMode
  }
}

val LocalRenderBudget = staticCompositionLocalOf { RenderBudget.FULL }

private val BrandColor = Color(0xFF1E88E5)

@Composable
fun Theme(content: @Composable () -> Unit) {
  val mode = ThemeSettings.mode.collectAsState().value
  val dark =
    when (mode) {
      ThemeMode.SYSTEM -> isSystemInDarkTheme()
      ThemeMode.LIGHT -> false
      ThemeMode.DARK -> true
    }
  val colorScheme = rememberDynamicColorScheme(seedColor = BrandColor, isDark = dark)
  MaterialTheme(colorScheme = colorScheme, content = content)
}
