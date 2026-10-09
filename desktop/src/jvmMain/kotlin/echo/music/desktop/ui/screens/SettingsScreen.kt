package echo.music.desktop.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import echo.music.desktop.auth.AuthSyncState
import echo.music.desktop.local.DesktopPreferences
import echo.music.desktop.local.LocalMediaScanner
import echo.music.desktop.ui.components.CookieInputDialog
import echo.music.desktop.ui.theme.ThemeMode
import echo.music.desktop.ui.theme.ThemeSettings
import echo.music.playback.EngineType
import echo.music.playback.PlaybackManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

enum class SettingsCategory(val title: String, val icon: ImageVector) {
  GENERAL("General & Desktop", Icons.Default.Settings),
  APPEARANCE("Appearance", Icons.Default.Palette),
  AUDIO("Audio & Playback", Icons.Default.GraphicEq),
  CONTENT("Content & Region", Icons.Default.Language),
  ACCOUNT("Account & Sync", Icons.Default.AccountCircle),
  INTEGRATIONS("Integrations", Icons.Default.Extension),
  STORAGE("Storage & Library", Icons.Default.Folder),
  ABOUT("About", Icons.Default.Info),
}

@Composable
fun SettingsScreen(
  preferences: DesktopPreferences,
  scanner: LocalMediaScanner,
  modifier: Modifier = Modifier,
) {
  var selectedCategory by remember { mutableStateOf(SettingsCategory.GENERAL) }
  var cookieDialogVisible by remember { mutableStateOf(false) }

  if (cookieDialogVisible) {
    CookieInputDialog(onDismiss = { cookieDialogVisible = false })
  }

  Row(modifier = modifier.fillMaxSize()) {
    // Left Sidebar: Categories Navigation
    SettingsSidebar(
      selectedCategory = selectedCategory,
      onSelectCategory = { selectedCategory = it },
      modifier = Modifier.width(240.dp).fillMaxHeight(),
    )

    // Divider separating sidebar and content
    Box(
      modifier =
        Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant)
    )

    // Right Content Area
    Column(
      modifier =
        Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(28.dp),
      verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      Text(
        text = selectedCategory.title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
      )

      when (selectedCategory) {
        SettingsCategory.GENERAL -> GeneralSettingsGroup(preferences)
        SettingsCategory.APPEARANCE -> AppearanceSettingsGroup(preferences)
        SettingsCategory.AUDIO -> AudioSettingsGroup(preferences)
        SettingsCategory.CONTENT -> ContentSettingsGroup(preferences)
        SettingsCategory.ACCOUNT ->
          AccountSettingsGroup(
            preferences = preferences,
            onManualCookieInput = { cookieDialogVisible = true },
          )
        SettingsCategory.INTEGRATIONS -> IntegrationsSettingsGroup(preferences)
        SettingsCategory.STORAGE -> StorageSettingsGroup(preferences, scanner)
        SettingsCategory.ABOUT -> AboutSettingsGroup()
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
private fun SettingsSidebar(
  selectedCategory: SettingsCategory,
  onSelectCategory: (SettingsCategory) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        .padding(vertical = 20.dp, horizontal = 12.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    Text(
      text = "Settings",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )

    Spacer(modifier = Modifier.height(8.dp))

    SettingsCategory.entries.forEach { category ->
      val isSelected = category == selectedCategory
      val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
      val contentColor =
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant

      Row(
        modifier =
          Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable { onSelectCategory(category) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = category.icon,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = category.title,
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
          color = contentColor,
        )
      }
    }
  }
}

@Composable
private fun SettingsGroupCard(
  title: String,
  description: String? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
      ),
    shape = RoundedCornerShape(16.dp),
  ) {
    Column(modifier = Modifier.padding(20.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      if (description != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(modifier = Modifier.height(16.dp))
      content()
    }
  }
}

@Composable
private fun SettingsToggleRow(
  title: String,
  description: String? = null,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      if (description != null) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Spacer(modifier = Modifier.width(16.dp))
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

// -------------------------------------------------------------
// 1. GENERAL & DESKTOP SETTINGS
// -------------------------------------------------------------
@Composable
private fun GeneralSettingsGroup(preferences: DesktopPreferences) {
  var closeToTray by remember { mutableStateOf(preferences.closeToTray) }
  var startMinimized by remember { mutableStateOf(preferences.startMinimized) }
  var mprisEnabled by remember { mutableStateOf(preferences.mprisEnabled) }
  var desktopNotifications by remember { mutableStateOf(preferences.desktopNotifications) }

  SettingsGroupCard(
    title = "Desktop Integration",
    description = "Linux system and desktop environment behaviors.",
  ) {
    SettingsToggleRow(
      title = "Close to System Tray",
      description =
        "Closing the main window will minimize it to the system tray instead of quitting.",
      checked = closeToTray,
      onCheckedChange = {
        closeToTray = it
        preferences.closeToTray = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Start Minimized",
      description = "Start the application in the system tray quietly without showing the window.",
      checked = startMinimized,
      onCheckedChange = {
        startMinimized = it
        preferences.startMinimized = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "MPRIS2 D-Bus Media Controls",
      description =
        "Expose play/pause/track metadata to Linux status bars (Waybar, Polybar, KDE, GNOME).",
      checked = mprisEnabled,
      onCheckedChange = {
        mprisEnabled = it
        preferences.mprisEnabled = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Desktop Notifications",
      description = "Show native desktop notifications whenever a new track starts playing.",
      checked = desktopNotifications,
      onCheckedChange = {
        desktopNotifications = it
        preferences.desktopNotifications = it
      },
    )
  }
}

// -------------------------------------------------------------
// 2. APPEARANCE SETTINGS
// -------------------------------------------------------------
@Composable
private fun AppearanceSettingsGroup(preferences: DesktopPreferences) {
  val currentMode by ThemeSettings.mode.collectAsState()
  var pureBlack by remember { mutableStateOf(preferences.pureBlack) }
  var dynamicPalette by remember { mutableStateOf(preferences.dynamicPalette) }
  var currentAccentColor by remember { mutableStateOf(preferences.accentColor) }
  var uiDensity by remember { mutableStateOf(preferences.uiDensity) }

  // Player & Artwork styling
  var playerBgStyle by remember { mutableStateOf(preferences.playerBackgroundStyle) }
  var thumbnailCornerRadius by remember { mutableStateOf(preferences.thumbnailCornerRadiusDp) }
  var cropAlbumArt by remember { mutableStateOf(preferences.cropAlbumArt) }
  var hidePlayerThumbnail by remember { mutableStateOf(preferences.hidePlayerThumbnail) }

  // Lyrics styling
  var lyricsBlur by remember { mutableStateOf(preferences.lyricsBlur) }
  var lyricsTextSize by remember { mutableFloatStateOf(preferences.lyricsTextSize) }
  var lyricsPosition by remember { mutableStateOf(preferences.lyricsPosition) }
  var lyricsGlow by remember { mutableStateOf(preferences.lyricsGlow) }

  val accentColors =
    listOf(
      "Blue (Default)" to 0xFF1E88E5,
      "Purple" to 0xFF8E24AA,
      "Teal" to 0xFF00897B,
      "Emerald" to 0xFF43A047,
      "Amber" to 0xFFFFB300,
      "Deep Orange" to 0xFFF4511E,
      "Crimson" to 0xFFE53935,
      "Pink" to 0xFFD81B60,
    )

  SettingsGroupCard(
    title = "Theme & Colors",
    description = "Customize dark mode behavior, AMOLED pure black, and color palettes.",
  ) {
    Text(
      text = "Theme Mode",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(6.dp))

    ThemeMode.entries.forEach { mode ->
      Row(
        modifier =
          Modifier.fillMaxWidth()
            .selectable(
              selected = currentMode == mode,
              onClick = {
                ThemeSettings.setMode(mode)
                preferences.themeMode = mode.name
              },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(
          selected = currentMode == mode,
          onClick = {
            ThemeSettings.setMode(mode)
            preferences.themeMode = mode.name
          },
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text =
            when (mode) {
              ThemeMode.SYSTEM -> "Follow System (Auto)"
              ThemeMode.LIGHT -> "Light Theme"
              ThemeMode.DARK -> "Dark Theme (Default)"
            },
          style = MaterialTheme.typography.bodyMedium,
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    SettingsToggleRow(
      title = "AMOLED Pure Black",
      description =
        "Use pitch-black (#000000) backgrounds in dark mode instead of standard dark surfaces.",
      checked = pureBlack,
      onCheckedChange = {
        pureBlack = it
        preferences.pureBlack = it
        ThemeSettings.setPureBlack(it)
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    SettingsToggleRow(
      title = "Dynamic Artwork Palette",
      description = "Extract vibrant accent colors automatically from currently playing album art.",
      checked = dynamicPalette,
      onCheckedChange = {
        dynamicPalette = it
        preferences.dynamicPalette = it
      },
    )

    if (!dynamicPalette) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "Accent Color Preset",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        accentColors.forEach { (_, colorVal) ->
          val isSelected = currentAccentColor == colorVal
          Box(
            modifier =
              Modifier.size(36.dp).clip(CircleShape).background(Color(colorVal)).clickable {
                currentAccentColor = colorVal
                preferences.accentColor = colorVal
                ThemeSettings.setSeedColor(Color(colorVal))
              },
            contentAlignment = Alignment.Center,
          ) {
            if (isSelected) {
              Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color.White))
            }
          }
        }
      }
    }
  }

  SettingsGroupCard(
    title = "Player & Album Art",
    description = "Controls visual presentation for album covers and player screens.",
  ) {
    Text(
      text = "Player Background Style",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(6.dp))
    listOf(
        "GRADIENT" to "Artwork Mesh Gradient",
        "BLUR" to "Glassmorphism & Live Blur",
        "DEFAULT" to "Solid Theme Background",
      )
      .forEach { (key, label) ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .selectable(
                selected = playerBgStyle == key,
                onClick = {
                  playerBgStyle = key
                  preferences.playerBackgroundStyle = key
                },
              )
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = playerBgStyle == key,
            onClick = {
              playerBgStyle = key
              preferences.playerBackgroundStyle = key
            },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
      }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = "Album Art Corner Radius",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${thumbnailCornerRadius} dp",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
      )
    }
    Slider(
      value = thumbnailCornerRadius.toFloat(),
      onValueChange = {
        thumbnailCornerRadius = it.roundToInt()
        preferences.thumbnailCornerRadiusDp = thumbnailCornerRadius
      },
      valueRange = 0f..24f,
      steps = 11,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Crop Album Artwork",
      description = "Crop square artwork slightly to eliminate non-standard borders and margins.",
      checked = cropAlbumArt,
      onCheckedChange = {
        cropAlbumArt = it
        preferences.cropAlbumArt = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Hide Album Cover in Bottom Bar",
      description = "Display track details without album cover thumbnail in the bottom player bar.",
      checked = hidePlayerThumbnail,
      onCheckedChange = {
        hidePlayerThumbnail = it
        preferences.hidePlayerThumbnail = it
      },
    )
  }

  SettingsGroupCard(
    title = "Lyrics Presentation",
    description = "Appearance options for synchronized and synced lyrics views.",
  ) {
    SettingsToggleRow(
      title = "Apple Music Style Backdrop Blur",
      description = "Render lush background blur animations behind active lyrics.",
      checked = lyricsBlur,
      onCheckedChange = {
        lyricsBlur = it
        preferences.lyricsBlur = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Active Line Glow Effect",
      description = "Add a radiant highlight glow behind the currently sung line.",
      checked = lyricsGlow,
      onCheckedChange = {
        lyricsGlow = it
        preferences.lyricsGlow = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    Text(
      text = "Lyrics Text Alignment",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Medium,
    )
    Spacer(modifier = Modifier.height(6.dp))
    listOf(
        "LEFT" to "Left Aligned",
        "CENTER" to "Center Aligned",
        "RIGHT" to "Right Aligned",
      )
      .forEach { (key, label) ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .selectable(
                selected = lyricsPosition == key,
                onClick = {
                  lyricsPosition = key
                  preferences.lyricsPosition = key
                },
              )
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = lyricsPosition == key,
            onClick = {
              lyricsPosition = key
              preferences.lyricsPosition = key
            },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
      }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = "Lyrics Font Size",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
      )
      Text(
        text = "${lyricsTextSize.roundToInt()} sp",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Bold,
      )
    }
    Slider(
      value = lyricsTextSize,
      onValueChange = {
        lyricsTextSize = it
        preferences.lyricsTextSize = it
      },
      valueRange = 16f..36f,
      steps = 10,
    )
  }

  SettingsGroupCard(
    title = "Interface Density",
    description = "Control spacing, paddings, and font sizes across lists and cards.",
  ) {
    listOf(
        "standard" to "Standard Density (Comfortable)",
        "compact" to "Compact Density (High Information Density)",
      )
      .forEach { (key, label) ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .selectable(
                selected = uiDensity == key,
                onClick = {
                  uiDensity = key
                  preferences.uiDensity = key
                },
              )
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = uiDensity == key,
            onClick = {
              uiDensity = key
              preferences.uiDensity = key
            },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
      }
  }
}

// -------------------------------------------------------------
// 3. AUDIO & PLAYBACK SETTINGS
// -------------------------------------------------------------
@Composable
private fun AudioSettingsGroup(preferences: DesktopPreferences) {
  val activeEngine by PlaybackManager.activeEngine.collectAsState()
  var configuredEngine by remember {
    mutableStateOf(
      preferences.engineType?.let { runCatching { EngineType.valueOf(it) }.getOrNull() }
        ?: EngineType.AUTO
    )
  }
  var volumeNormalization by remember { mutableStateOf(preferences.volumeNormalization) }
  var crossfadeEnabled by remember { mutableStateOf(preferences.crossfadeEnabled) }
  var crossfadeDuration by remember { mutableFloatStateOf(preferences.crossfadeDurationSeconds) }
  var skipSilence by remember { mutableStateOf(preferences.skipSilence) }

  SettingsGroupCard(
    title = "Playback Engine",
    description = "Select the multimedia decoding engine for stream and local audio playback.",
  ) {
    listOf(
        EngineType.AUTO to "Auto (Recommended: libmpv, then GStreamer, then Fallback)",
        EngineType.MPV to "MPV (Direct native libmpv engine, fast gapless playback)",
        EngineType.GSTREAMER to "GStreamer (Linux system multimedia framework)",
        EngineType.FALLBACK to "Fallback (Built-in Java AudioSystem)",
      )
      .forEach { (engine, label) ->
        Row(
          modifier =
            Modifier.fillMaxWidth()
              .selectable(
                selected = configuredEngine == engine,
                onClick = {
                  configuredEngine = engine
                  preferences.engineType = engine.name
                  PlaybackManager.setEngine(engine)
                },
              )
              .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          RadioButton(
            selected = configuredEngine == engine,
            onClick = {
              configuredEngine = engine
              preferences.engineType = engine.name
              PlaybackManager.setEngine(engine)
            },
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
      }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Active runtime engine: $activeEngine",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.primary,
      fontWeight = FontWeight.Medium,
    )
  }

  SettingsGroupCard(
    title = "Tuning & Audio Quality",
    description = "Adjust audio normalization, crossfade transitions, and silence skipping.",
  ) {
    SettingsToggleRow(
      title = "Volume Normalization (ReplayGain)",
      description = "Prevents sudden volume spikes across different albums and YouTube tracks.",
      checked = volumeNormalization,
      onCheckedChange = {
        volumeNormalization = it
        preferences.volumeNormalization = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Crossfade",
      description = "Smoothly blend track transitions.",
      checked = crossfadeEnabled,
      onCheckedChange = {
        crossfadeEnabled = it
        preferences.crossfadeEnabled = it
      },
    )

    if (crossfadeEnabled) {
      Column(modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(text = "Duration", style = MaterialTheme.typography.bodySmall)
          Text(
            text = "${crossfadeDuration.roundToInt()} seconds",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
          )
        }
        Slider(
          value = crossfadeDuration,
          onValueChange = {
            crossfadeDuration = it
            preferences.crossfadeDurationSeconds = it
          },
          valueRange = 1f..12f,
          steps = 10,
        )
      }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Skip Silence",
      description = "Detect and skip long silent intros or outros in tracks automatically.",
      checked = skipSilence,
      onCheckedChange = {
        skipSilence = it
        preferences.skipSilence = it
      },
    )
  }
}

// -------------------------------------------------------------
// 4. CONTENT & LOCALIZATION SETTINGS
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentSettingsGroup(preferences: DesktopPreferences) {
  var hideExplicit by remember { mutableStateOf(preferences.hideExplicit) }
  var hideVideoTracks by remember { mutableStateOf(preferences.hideVideoTracks) }
  var autoplaySimilar by remember { mutableStateOf(preferences.autoplaySimilar) }
  var country by remember { mutableStateOf(preferences.contentCountry) }
  var language by remember { mutableStateOf(preferences.contentLanguage) }

  val countries =
    listOf(
      "US" to "United States",
      "IN" to "India",
      "GB" to "United Kingdom",
      "CA" to "Canada",
      "DE" to "Germany",
      "FR" to "France",
      "JP" to "Japan",
      "BR" to "Brazil",
      "AU" to "Australia",
    )

  val languages =
    listOf(
      "en" to "English",
      "hi" to "Hindi",
      "es" to "Spanish",
      "fr" to "French",
      "de" to "German",
      "ja" to "Japanese",
      "ko" to "Korean",
      "pt" to "Portuguese",
    )

  SettingsGroupCard(
    title = "Content Filtering",
    description = "Filter explicit lyrics or video-only media from results.",
  ) {
    SettingsToggleRow(
      title = "Hide Explicit Songs",
      description = "Filter out songs tagged with parental advisory explicit markers.",
      checked = hideExplicit,
      onCheckedChange = {
        hideExplicit = it
        preferences.hideExplicit = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Hide Music Videos",
      description = "Prefer studio audio tracks and hide video versions when available.",
      checked = hideVideoTracks,
      onCheckedChange = {
        hideVideoTracks = it
        preferences.hideVideoTracks = it
      },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Autoplay Similar Music",
      description = "Automatically queue and play similar tracks when the current queue ends.",
      checked = autoplaySimilar,
      onCheckedChange = {
        autoplaySimilar = it
        preferences.autoplaySimilar = it
      },
    )
  }

  SettingsGroupCard(
    title = "Region & Localization",
    description = "Content region and language for charts, explore trending, and search.",
  ) {
    var countryExpanded by remember { mutableStateOf(false) }
    var langExpanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
      // Country Dropdown
      ExposedDropdownMenuBox(
        expanded = countryExpanded,
        onExpandedChange = { countryExpanded = !countryExpanded },
        modifier = Modifier.weight(1f),
      ) {
        OutlinedTextField(
          value = countries.find { it.first == country }?.second ?: country,
          onValueChange = {},
          readOnly = true,
          label = { Text("Content Country") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = countryExpanded) },
          modifier =
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
          expanded = countryExpanded,
          onDismissRequest = { countryExpanded = false },
        ) {
          countries.forEach { (code, name) ->
            DropdownMenuItem(
              text = { Text("$name ($code)") },
              onClick = {
                country = code
                preferences.contentCountry = code
                countryExpanded = false
              },
            )
          }
        }
      }

      // Language Dropdown
      ExposedDropdownMenuBox(
        expanded = langExpanded,
        onExpandedChange = { langExpanded = !langExpanded },
        modifier = Modifier.weight(1f),
      ) {
        OutlinedTextField(
          value = languages.find { it.first == language }?.second ?: language,
          onValueChange = {},
          readOnly = true,
          label = { Text("Content Language") },
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = langExpanded) },
          modifier =
            Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
          expanded = langExpanded,
          onDismissRequest = { langExpanded = false },
        ) {
          languages.forEach { (code, name) ->
            DropdownMenuItem(
              text = { Text("$name ($code)") },
              onClick = {
                language = code
                preferences.contentLanguage = code
                langExpanded = false
              },
            )
          }
        }
      }
    }
  }
}

// -------------------------------------------------------------
// 5. ACCOUNT & SYNC (UI ONLY AS REQUESTED)
// -------------------------------------------------------------
@Composable
private fun AccountSettingsGroup(
  preferences: DesktopPreferences,
  onManualCookieInput: () -> Unit,
) {
  val status by AuthSyncState.status.collectAsState()
  var instructionsVisible by remember { mutableStateOf(false) }
  var syncLikedSongs by remember { mutableStateOf(true) }
  var syncPlaylists by remember { mutableStateOf(true) }
  var useLoginForRecommendations by remember { mutableStateOf(true) }

  // User Profile Card
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
      ),
    shape = RoundedCornerShape(16.dp),
  ) {
    Row(
      modifier = Modifier.padding(20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (status.avatarUrl != null) {
        AsyncImage(
          model = status.avatarUrl,
          contentDescription = status.accountName ?: "Account Avatar",
          modifier = Modifier.size(54.dp).clip(CircleShape),
          contentScale = ContentScale.Crop,
        )
      } else {
        Box(
          modifier =
            Modifier.size(54.dp)
              .clip(CircleShape)
              .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            Icons.Default.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(36.dp),
          )
        }
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text =
            status.accountName ?: if (status.synced) "YouTube Music Account" else "Guest Account",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
        )
        if (!status.accountEmail.isNullOrBlank()) {
          Text(
            text = status.accountEmail.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Text(
          text =
            if (status.synced)
              "Connected via Companion Extension (${status.cookieNames.size} cookies)"
            else "Not synced. Login via companion extension to sync your playlists & likes.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (status.synced) {
        OutlinedButton(
          onClick = {
            YouTube.cookie = null
            YouTube.visitorData = null
            YouTube.dataSyncId = null
            preferences.clearAuth()
            AuthSyncState.reset()
          }
        ) {
          Text("Sign Out")
        }
      }
    }
  }

  SettingsGroupCard(
    title = "Authentication & Extension Sync",
    description = "Sync cookies securely from your browser via the companion extension.",
  ) {
    Text(
      text =
        if (status.synced) {
          val syncedAt =
            status.lastSyncEpochMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
              ?: "unknown"
          "Status: Active · Last synced: $syncedAt"
        } else {
          "Status: Companion extension not connected."
        },
      style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedButton(onClick = { instructionsVisible = !instructionsVisible }) {
        Icon(
          Icons.AutoMirrored.Filled.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Setup Guide")
      }
      FilledTonalButton(onClick = onManualCookieInput) {
        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Manual Cookie Input…")
      }
    }

    AnimatedVisibility(visible = instructionsVisible) {
      Column(
        modifier =
          Modifier.padding(top = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
      ) {
        Text(
          text = "Companion Extension Instructions:",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text =
            "1. Open chrome://extensions (or your browser's extension settings).\n" +
              "2. Enable 'Developer mode'.\n" +
              "3. Click 'Load unpacked' and select the 'companion-extension' folder from the Echo Music repository.\n" +
              "4. Open music.youtube.com and sign in.\n" +
              "5. Click the Echo Music Companion extension icon and select 'Sync to Echo Music'.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }

  SettingsGroupCard(
    title = "Cloud Synchronization (Mock UI)",
    description = "Preferences for syncing data with your YouTube Music account.",
  ) {
    SettingsToggleRow(
      title = "Sync Liked Songs",
      description = "Automatically pull and sync your liked songs from YouTube Music.",
      checked = syncLikedSongs,
      onCheckedChange = { syncLikedSongs = it },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Sync Playlists",
      description = "Synchronize created and saved playlists with your account.",
      checked = syncPlaylists,
      onCheckedChange = { syncPlaylists = it },
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "Personalized Recommendations",
      description = "Use your account listening history to customize the Home feed.",
      checked = useLoginForRecommendations,
      onCheckedChange = { useLoginForRecommendations = it },
    )
  }
}

// -------------------------------------------------------------
// 6. INTEGRATIONS SETTINGS
// -------------------------------------------------------------
@Composable
private fun IntegrationsSettingsGroup(preferences: DesktopPreferences) {
  var discordRpc by remember { mutableStateOf(preferences.discordRpcEnabled) }
  var discordShowCover by remember { mutableStateOf(preferences.discordShowCover) }
  var lastFmEnabled by remember { mutableStateOf(preferences.lastFmEnabled) }
  var lastFmUsername by remember { mutableStateOf(preferences.lastFmUsername) }
  var listenBrainzEnabled by remember { mutableStateOf(preferences.listenBrainzEnabled) }
  var listenBrainzToken by remember { mutableStateOf(preferences.listenBrainzToken) }

  SettingsGroupCard(
    title = "Discord Rich Presence",
    description =
      "Display currently playing song, artist, and elapsed time on your Discord profile.",
  ) {
    SettingsToggleRow(
      title = "Enable Discord Rich Presence",
      description = "Connects to local Discord RPC socket on Linux (/run/user/1000/discord-ipc-0).",
      checked = discordRpc,
      onCheckedChange = {
        discordRpc = it
        preferences.discordRpcEnabled = it
      },
    )

    if (discordRpc) {
      HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
      SettingsToggleRow(
        title = "Display Album Cover in Rich Presence",
        description = "Show track cover art as large activity image on Discord.",
        checked = discordShowCover,
        onCheckedChange = {
          discordShowCover = it
          preferences.discordShowCover = it
        },
      )
    }
  }

  SettingsGroupCard(
    title = "Scrobbling Services",
    description = "Log your music listening history automatically to Last.fm or ListenBrainz.",
  ) {
    SettingsToggleRow(
      title = "Last.fm Scrobbler",
      description = "Scrobble played tracks to your Last.fm profile.",
      checked = lastFmEnabled,
      onCheckedChange = {
        lastFmEnabled = it
        preferences.lastFmEnabled = it
      },
    )

    if (lastFmEnabled) {
      OutlinedTextField(
        value = lastFmUsername,
        onValueChange = {
          lastFmUsername = it
          preferences.lastFmUsername = it
        },
        label = { Text("Last.fm Username") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
      )
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    SettingsToggleRow(
      title = "ListenBrainz Scrobbler",
      description = "Open source music scrobbling by MetaBrainz.",
      checked = listenBrainzEnabled,
      onCheckedChange = {
        listenBrainzEnabled = it
        preferences.listenBrainzEnabled = it
      },
    )

    if (listenBrainzEnabled) {
      OutlinedTextField(
        value = listenBrainzToken,
        onValueChange = {
          listenBrainzToken = it
          preferences.listenBrainzToken = it
        },
        label = { Text("ListenBrainz User Token") },
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
      )
    }
  }
}

// -------------------------------------------------------------
// 7. STORAGE & LOCAL LIBRARY SETTINGS
// -------------------------------------------------------------
@Composable
private fun StorageSettingsGroup(
  preferences: DesktopPreferences,
  scanner: LocalMediaScanner,
) {
  val roots by scanner.roots.collectAsState()
  val isScanning by scanner.isScanning.collectAsState()
  var maxAudioCache by remember { mutableStateOf(preferences.maxAudioCacheMb) }
  var maxImageCache by remember { mutableStateOf(preferences.maxImageCacheMb) }
  var backupMessage by remember { mutableStateOf<String?>(null) }

  SettingsGroupCard(
    title = "Local Music Folders",
    description = "Watched filesystem directories for local MP3, FLAC, M4A, and AAC media files.",
  ) {
    if (roots.isEmpty()) {
      Text(
        text = "No folders watched yet. Click 'Add folder' to select a music directory.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    roots.forEach { root ->
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          Icons.Default.Folder,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = root,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { scanner.removeRoot(root) }) {
          Icon(Icons.Default.Delete, contentDescription = "Remove folder")
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      FilledTonalButton(onClick = { chooseDirectory()?.let { scanner.addRoot(it) } }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Add folder")
      }
      OutlinedButton(onClick = { scanner.rescan() }, enabled = !isScanning) {
        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(if (isScanning) "Scanning…" else "Rescan now")
      }
    }
  }

  SettingsGroupCard(
    title = "Cache & Storage Limits",
    description =
      "Control local disk storage used for downloaded artwork and decoded audio streams.",
  ) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = "Audio stream cache limit: ${maxAudioCache} MB",
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(
        onClick = {
          // Clear audio cache logic
        }
      ) {
        Text("Clear Audio Cache")
      }
    }
    Slider(
      value = maxAudioCache.toFloat(),
      onValueChange = {
        maxAudioCache = it.roundToInt()
        preferences.maxAudioCacheMb = maxAudioCache
      },
      valueRange = 256f..4096f,
      steps = 15,
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      Text(
        text = "Image thumbnail cache limit: ${maxImageCache} MB",
        style = MaterialTheme.typography.bodyMedium,
      )
      TextButton(
        onClick = {
          // Clear image cache logic
        }
      ) {
        Text("Clear Image Cache")
      }
    }
    Slider(
      value = maxImageCache.toFloat(),
      onValueChange = {
        maxImageCache = it.roundToInt()
        preferences.maxImageCacheMb = maxImageCache
      },
      valueRange = 128f..2048f,
      steps = 15,
    )
  }

  SettingsGroupCard(
    title = "Backup & Restore",
    description = "Export and import your Echo Music settings as JSON clipboard data.",
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      FilledTonalButton(
        onClick = {
          val json = preferences.exportSettingsJson()
          Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(json), null)
          backupMessage = "Settings exported & copied to clipboard!"
        }
      ) {
        Text("Export Settings (Copy)")
      }

      OutlinedButton(
        onClick = {
          val clipboard = Toolkit.getDefaultToolkit().systemClipboard
          val text = runCatching {
            clipboard.getData(DataFlavor.stringFlavor) as? String
          }
            .getOrNull()
          if (!text.isNullOrBlank() && preferences.importSettingsJson(text)) {
            backupMessage = "Settings successfully restored from clipboard!"
          } else {
            backupMessage = "Failed to import: clipboard does not contain valid settings JSON."
          }
        }
      ) {
        Text("Import Settings (Paste)")
      }
    }

    if (backupMessage != null) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = backupMessage ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Medium,
      )
    }
  }
}

// -------------------------------------------------------------
// 8. ABOUT SETTINGS
// -------------------------------------------------------------
@Composable
private fun AboutSettingsGroup() {
  SettingsGroupCard(
    title = "Echo Music Desktop",
    description = "Fast, native client for YouTube Music & Local Audio on Linux.",
  ) {
    Text(
      text = "Version: 1.0.0-dev (Linux x86_64)",
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text =
        "JVM Runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text =
        "OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    Text(
      text =
        "Echo Music is free and open-source software released under the GNU General Public License v3.0.",
      style = MaterialTheme.typography.bodyMedium,
    )

    Spacer(modifier = Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      OutlinedButton(
        onClick = {
          runCatching {
            java.awt.Desktop.getDesktop()
              .browse(java.net.URI("https://github.com/VardhmanSurana/Echo-Music"))
          }
        }
      ) {
        Icon(
          Icons.AutoMirrored.Filled.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("GitHub Repository")
      }

      OutlinedButton(
        onClick = {
          runCatching {
            java.awt.Desktop.getDesktop().browse(java.net.URI("https://discord.gg/echomusic"))
          }
        }
      ) {
        Icon(
          Icons.AutoMirrored.Filled.OpenInNew,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Discord Community")
      }
    }
  }
}
