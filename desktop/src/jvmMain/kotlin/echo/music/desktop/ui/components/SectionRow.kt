package echo.music.desktop.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem

@Composable
fun SectionRow(
  title: String,
  items: List<YTItem>,
  onSongClick: (SongItem) -> Unit,
  onOpenItem: (YTItem) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
      )
      Spacer(modifier = Modifier.weight(1f))
      val firstSong = items.firstOrNull { it is SongItem } as? SongItem
      if (firstSong != null) {
        TextButton(onClick = { onSongClick(firstSong) }) {
          Text("Play all", style = MaterialTheme.typography.labelMedium)
        }
      }
    }
    LazyRow(
      contentPadding = PaddingValues(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      items(items, key = { "${it::class.simpleName}:${it.id}:${it.title}" }) { item ->
        MediaCard(
          item = item,
          onClick = {
            if (item is SongItem) {
              onSongClick(item)
            } else {
              onOpenItem(item)
            }
          },
        )
      }
    }
  }
}
