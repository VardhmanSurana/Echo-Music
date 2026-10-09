package echo.music.desktop.ui.screens

import com.music.innertube.YouTube
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import echo.music.desktop.playback.DesktopPlaybackResolver

internal suspend fun DesktopPlaybackResolver.openAndPlay(item: YTItem) {
  when (item) {
    is SongItem -> playSong(item)
    is AlbumItem ->
      YouTube.album(item.browseId)
        .fold(
          onSuccess = { playQueue(it.songs, 0) },
          onFailure = { showError(it.message ?: "Failed to open album") },
        )
    is PlaylistItem ->
      YouTube.playlist(item.id)
        .fold(
          onSuccess = { playQueue(it.songs, 0) },
          onFailure = { showError(it.message ?: "Failed to open playlist") },
        )
    is ArtistItem -> Unit
    else -> Unit
  }
}
