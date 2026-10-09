package echo.music.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.music.innertube.YouTube

@Composable
fun CookieInputDialog(onDismiss: () -> Unit) {
  var text by remember { mutableStateOf("") }
  Dialog(visible = true, onCloseRequest = onDismiss, title = "Manual Cookie Input") {
    Surface {
      Column(modifier = Modifier.padding(16.dp)) {
        Text(
          "Paste the full Cookie header from an authenticated youtube.com session.",
          style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
          value = text,
          onValueChange = { text = it },
          label = { Text("Cookie header") },
          modifier = Modifier.width(420.dp).height(120.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.width(420.dp)) {
          TextButton(onClick = onDismiss) { Text("Cancel") }
          Spacer(modifier = Modifier.width(8.dp))
          Button(
            onClick = {
              YouTube.cookie = text.trim().ifBlank { null }
              onDismiss()
            }
          ) {
            Text("Save")
          }
        }
      }
    }
  }
}
