package echo.music.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import echo.music.desktop.local.LocalAudioFile
import echo.music.desktop.local.LocalMediaScanner
import echo.music.desktop.ui.components.TrackList
import echo.music.desktop.ui.components.TrackUi
import echo.music.playback.PlaybackManager
import java.awt.GraphicsEnvironment
import javax.swing.JFileChooser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine

class LocalMusicStateHolder(private val scanner: LocalMediaScanner) {
  private val _query = MutableStateFlow("")
  val query: StateFlow<String> = _query.asStateFlow()

  fun setQuery(value: String) {
    _query.value = value
  }

  val files: Flow<List<LocalAudioFile>> =
    combine(scanner.result, _query) { result, query ->
      if (query.isBlank()) {
        result.files
      } else {
        result.files.filter { file ->
          file.title.contains(query, ignoreCase = true) ||
            file.artist?.contains(query, ignoreCase = true) == true ||
            file.album?.contains(query, ignoreCase = true) == true
        }
      }
    }
}

internal fun chooseDirectory(): String? {
  if (GraphicsEnvironment.isHeadless()) return null
  return runCatching {
    val chooser = JFileChooser()
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    chooser.dialogTitle = "Add music folder"
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
      chooser.selectedFile?.absolutePath
    } else {
      null
    }
  }
    .getOrNull()
}

@Composable
fun LocalMusicScreen(
  scanner: LocalMediaScanner,
  modifier: Modifier = Modifier,
) {
  val holder = remember(scanner) { LocalMusicStateHolder(scanner) }
  val query by holder.query.collectAsState()
  val files by holder.files.collectAsState(initial = emptyList())
  val roots by scanner.roots.collectAsState()
  val scanResult by scanner.result.collectAsState()
  val isScanning by scanner.isScanning.collectAsState()
  var errorsVisible by remember { mutableStateOf(false) }

  if (errorsVisible) {
    ScanErrorsDialog(errors = scanResult.errors, onDismiss = { errorsVisible = false })
  }

  Row(modifier = modifier.fillMaxSize()) {
    DirectoryPanel(
      roots = roots,
      onAdd = { chooseDirectory()?.let { scanner.addRoot(it) } },
      onRemove = { scanner.removeRoot(it) },
      modifier = Modifier.width(190.dp).fillMaxHeight(),
    )
    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
    Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(12.dp)) {
      OutlinedTextField(
        value = query,
        onValueChange = { holder.setQuery(it) },
        placeholder = { Text("Search local music…") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      Spacer(modifier = Modifier.padding(4.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "${files.size} tracks · ${scanResult.scannedDirs.size} folders",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isScanning) {
          Spacer(modifier = Modifier.width(12.dp))
          CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Scanning…",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (scanResult.errors.isNotEmpty()) {
          Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.padding(end = 8.dp),
          ) {
            TextButton(onClick = { errorsVisible = true }) {
              Text(
                text = "${scanResult.errors.size} errors",
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
            }
          }
        }
        TextButton(onClick = { scanner.rescan() }, enabled = !isScanning) { Text("Rescan") }
      }
      if (isScanning) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
      }
      if (files.isEmpty() && !isScanning) {
        Column(
          modifier = Modifier.weight(1f).fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Text(
            text =
              if (roots.isEmpty()) "Add a folder to start listening to your local music."
              else "No matching audio files found.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        TrackList(
          tracks = files.map { TrackUi.from(it) },
          onPlay = { index -> PlaybackManager.playQueue(files.map { it.toPlaybackItem() }, index) },
          modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
        )
      }
    }
  }
}

@Composable
private fun DirectoryPanel(
  roots: List<String>,
  onAdd: () -> Unit,
  onRemove: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.padding(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(text = "Folders", style = MaterialTheme.typography.titleSmall)
      Spacer(modifier = Modifier.weight(1f))
      IconButton(onClick = onAdd) {
        Icon(Icons.Default.Add, contentDescription = "Add folder")
      }
    }
    HorizontalDivider()
    if (roots.isEmpty()) {
      Text(
        text = "No folders watched yet.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
      )
    } else {
      LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        items(roots, key = { it }) { root ->
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = root,
              style = MaterialTheme.typography.bodySmall,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onRemove(root) }) {
              Icon(
                Icons.Default.Delete,
                contentDescription = "Remove folder",
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ScanErrorsDialog(errors: List<String>, onDismiss: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Scan errors") },
    text = {
      LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(errors) { error ->
          Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
          )
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
  )
}
