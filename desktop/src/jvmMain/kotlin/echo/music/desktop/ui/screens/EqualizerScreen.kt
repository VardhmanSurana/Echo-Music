package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import echo.music.desktop.local.DesktopPreferences
import echo.music.playback.PlaybackManager

private data class EqPreset(
  val name: String,
  val bands: List<Float>,
)

private val PRESETS =
  listOf(
    EqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Bass Boost", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    EqPreset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 2f, 4f, 6f, 7f)),
    EqPreset("Vocal", listOf(-2f, -1f, 1f, 3f, 4f, 4f, 3f, 1f, 0f, -1f)),
    EqPreset("Rock", listOf(5f, 4f, 2f, -1f, -2f, -1f, 1f, 3f, 4f, 5f)),
    EqPreset("Pop", listOf(2f, 3f, 4f, 2f, 0f, 1f, 2f, 3f, 4f, 4f)),
    EqPreset("Electronic", listOf(6f, 5f, 3f, 0f, -1f, 1f, 2f, 4f, 5f, 6f)),
    EqPreset("Jazz", listOf(3f, 2f, 1f, 2f, -1f, -1f, 0f, 1f, 2f, 3f)),
    EqPreset("Classical", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
  )

private val BAND_LABELS =
  listOf("31Hz", "63Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

@Composable
fun EqualizerScreen(
  preferences: DesktopPreferences,
  modifier: Modifier = Modifier,
) {
  var isEnabled by remember { mutableStateOf(preferences.equalizerEnabled) }
  var currentPresetName by remember { mutableStateOf(preferences.equalizerPreset) }
  var bands by remember { mutableStateOf(preferences.equalizerBands) }
  var bassBoost by remember { mutableFloatStateOf(preferences.bassBoost) }
  var speed by remember { mutableFloatStateOf(preferences.playbackSpeed) }

  val activeEngine by PlaybackManager.activeEngine.collectAsState()

  fun applyEq() {
    preferences.equalizerEnabled = isEnabled
    preferences.equalizerPreset = currentPresetName
    preferences.equalizerBands = bands
    preferences.bassBoost = bassBoost

    if (isEnabled) {
      val effectiveBands = bands.mapIndexed { index, gain ->
        val extraBass =
          if (index == 0) bassBoost * 6f
          else if (index == 1) bassBoost * 4f else if (index == 2) bassBoost * 2f else 0f
        (gain + extraBass).coerceIn(-12f, 12f)
      }
      PlaybackManager.setEqualizer(effectiveBands)
    } else {
      PlaybackManager.setEqualizer(emptyList())
    }
  }

  fun applySpeed(newSpeed: Float) {
    speed = newSpeed
    preferences.playbackSpeed = newSpeed
    PlaybackManager.setPlaybackSpeed(newSpeed)
  }

  LaunchedEffect(Unit) {
    applyEq()
    applySpeed(speed)
  }

  Column(
    modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          Icons.Default.GraphicEq,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(text = "EQ & Audio", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Active engine: $activeEngine",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = if (isEnabled) "Enabled" else "Bypassed",
          style = MaterialTheme.typography.labelLarge,
          color =
            if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = isEnabled,
          onCheckedChange = {
            isEnabled = it
            applyEq()
          },
        )
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "Presets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
          OutlinedButton(
            onClick = {
              currentPresetName = "Flat"
              bands = List(10) { 0f }
              bassBoost = 0f
              applyEq()
            },
            enabled = isEnabled,
          ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset Flat")
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PRESETS.take(5).forEach { preset ->
            FilterChip(
              selected = currentPresetName == preset.name,
              onClick = {
                currentPresetName = preset.name
                bands = preset.bands
                applyEq()
              },
              label = { Text(preset.name) },
              enabled = isEnabled,
            )
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          PRESETS.drop(5).forEach { preset ->
            FilterChip(
              selected = currentPresetName == preset.name,
              onClick = {
                currentPresetName = preset.name
                bands = preset.bands
                applyEq()
              },
              label = { Text(preset.name) },
              enabled = isEnabled,
            )
          }
        }
      }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          text = "10-Band Equalizer (-12dB to +12dB)",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
          BAND_LABELS.forEachIndexed { index, label ->
            val gain = bands.getOrElse(index) { 0f }
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            ) {
              Text(
                text =
                  "${if (gain > 0f) "+" else ""}${String.format(java.util.Locale.US, "%.1f", gain)}",
                style = MaterialTheme.typography.labelSmall,
                color =
                  if (isEnabled && gain != 0f) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
              )
              Slider(
                value = gain,
                onValueChange = { newVal ->
                  val updated = bands.toMutableList()
                  updated[index] = newVal
                  bands = updated
                  currentPresetName = "Custom"
                  applyEq()
                },
                valueRange = -12f..12f,
                enabled = isEnabled,
                modifier = Modifier.height(140.dp),
              )
              Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
              )
            }
          }
        }
      }
    }

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = "Bass Boost", style = MaterialTheme.typography.titleSmall)
            Text(
              text = "Sub-bass harmonic low-end enhancement",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Text(
            text = "${(bassBoost * 100).toInt()}%",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
        }
        Slider(
          value = bassBoost,
          onValueChange = {
            bassBoost = it
            applyEq()
          },
          valueRange = 0f..1f,
          enabled = isEnabled,
          modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              Icons.Default.Speed,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(text = "Playback Speed", style = MaterialTheme.typography.titleSmall)
              Text(
                text = "Adjust pitch-preserving tempo (0.5x - 2.0x)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          OutlinedButton(onClick = { applySpeed(1.0f) }) { Text("1.0x (Normal)") }
        }
        Slider(
          value = speed,
          onValueChange = { applySpeed(it) },
          valueRange = 0.5f..2.0f,
          steps = 14,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}
