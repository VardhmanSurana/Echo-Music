package echo.music.desktop.system

import echo.music.playback.PlaybackManager
import echo.music.playback.PlaybackMediaItem
import echo.music.playback.PlaybackState
import echo.music.playback.RepeatMode
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.freedesktop.dbus.ObjectPath
import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.annotations.DBusProperty
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.errors.PropertyReadOnly
import org.freedesktop.dbus.errors.UnknownProperty
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.interfaces.Properties
import org.freedesktop.dbus.types.Variant

internal object MprisMapper {
  const val BUS_NAME = "org.mpris.MediaPlayer2.EchoMusic"
  const val OBJECT_PATH = "/org/mpris/MediaPlayer2"
  const val ROOT_INTERFACE = "org.mpris.MediaPlayer2"
  const val PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player"
  const val TRACK_ID_PREFIX = "/org/mpris/MediaPlayer2/Track"
  const val IDENTITY = "Echo Music"

  fun playbackStatus(state: PlaybackState): String =
    when (state) {
      PlaybackState.Playing,
      PlaybackState.Loading,
      is PlaybackState.Buffering -> "Playing"
      PlaybackState.Paused -> "Paused"
      PlaybackState.Idle,
      PlaybackState.Stopped,
      is PlaybackState.Failed -> "Stopped"
    }

  fun trackIdPath(id: String): String {
    val sanitized =
      id
        .map { c ->
          if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_') c else '_'
        }
        .joinToString("")
        .ifEmpty { "track" }
    return "$TRACK_ID_PREFIX/$sanitized"
  }

  fun metadata(item: PlaybackMediaItem?): Map<String, Any> {
    if (item == null) return emptyMap()
    val values = HashMap<String, Any>()
    values["mpris:trackid"] = ObjectPath("", trackIdPath(item.id))
    if (item.durationMs > 0) {
      values["mpris:length"] = msToMicros(item.durationMs)
    }
    values["xesam:title"] = item.title
    if (item.artist.isNotBlank()) {
      values["xesam:artist"] = arrayListOf(item.artist)
    }
    if (item.album.isNotBlank()) {
      values["xesam:album"] = item.album
    }
    item.artworkUrl?.let { values["mpris:artUrl"] = it }
    return values
  }

  fun msToMicros(ms: Long): Long = ms * 1000

  fun microsToMs(micros: Long): Long = micros / 1000

  fun loopStatusOf(mode: RepeatMode): String =
    when (mode) {
      RepeatMode.OFF -> "None"
      RepeatMode.ONE -> "Track"
      RepeatMode.ALL -> "Playlist"
    }

  fun repeatModeOf(loopStatus: String): RepeatMode =
    when (loopStatus) {
      "Track" -> RepeatMode.ONE
      "Playlist" -> RepeatMode.ALL
      else -> RepeatMode.OFF
    }

  fun shuffleToMpris(enabled: Boolean): Boolean = enabled

  fun shuffleFromMpris(shuffle: Boolean): Boolean = shuffle

  fun volumeOf(volume: Float): Double = volume.coerceIn(0f, 1f).toDouble()

  fun volumeLevel(volume: Double): Float = volume.toFloat().coerceIn(0f, 1f)
}

@DBusInterfaceName(MprisMapper.ROOT_INTERFACE)
@DBusProperty(name = "CanQuit", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanRaise", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "HasTrackList", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "Identity", type = String::class, access = DBusProperty.Access.READ)
@DBusProperty(
  name = "SupportedUriSchemes",
  type = List::class,
  access = DBusProperty.Access.READ,
)
@DBusProperty(
  name = "SupportedMimeTypes",
  type = List::class,
  access = DBusProperty.Access.READ,
)
internal interface MprisRoot : DBusInterface {
  fun Raise()

  fun Quit()
}

@DBusInterfaceName(MprisMapper.PLAYER_INTERFACE)
@DBusProperty(name = "PlaybackStatus", type = String::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "LoopStatus", type = String::class)
@DBusProperty(name = "Rate", type = Double::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "Shuffle", type = Boolean::class)
@DBusProperty(name = "Metadata", type = Map::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "Volume", type = Double::class)
@DBusProperty(name = "Position", type = Long::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "MinimumRate", type = Double::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "MaximumRate", type = Double::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanGoNext", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanGoPrevious", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanPause", type = Boolean::class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean::class, access = DBusProperty.Access.READ)
internal interface MprisPlayer : DBusInterface {
  fun Next()

  fun Previous()

  fun Pause()

  fun Play()

  fun PlayPause()

  fun Stop()

  fun Seek(offset: Long)

  fun SetPosition(trackId: ObjectPath, position: Long)

  fun OpenUri(uri: String)
}

class MprisService(
  private val onRaise: () -> Unit = {},
  private val onQuit: () -> Unit = {},
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {

  private val repeatMode = MutableStateFlow(RepeatMode.OFF)
  private val shuffle = MutableStateFlow(false)

  private var connection: DBusConnection? = null
  private var exporter: DBusInterface? = null
  private var observeJob: Job? = null

  var isAvailable: Boolean = false
    private set

  fun start() {
    if (isAvailable) return
    val root = MprisExporter()
    var conn: DBusConnection? = null
    try {
      conn = DBusConnection.getConnection(DBusConnection.DBusBusType.SESSION)
      conn.requestBusName(MprisMapper.BUS_NAME)
      conn.exportObject(MprisMapper.OBJECT_PATH, root)
    } catch (e: Exception) {
      try {
        conn?.close()
      } catch (_: Exception) {}
      connection = null
      exporter = null
      isAvailable = false
      return
    }
    connection = conn
    exporter = root
    isAvailable = true
    observeJob = scope.launch { observePlayback() }
  }

  fun release() {
    observeJob?.cancel()
    observeJob = null
    exporter = null
    isAvailable = false
    val conn = connection
    connection = null
    if (conn != null) {
      try {
        conn.unExportObject(MprisMapper.OBJECT_PATH)
      } catch (_: Exception) {}
      try {
        conn.releaseBusName(MprisMapper.BUS_NAME)
      } catch (_: Exception) {}
      try {
        conn.close()
      } catch (_: Exception) {}
    }
  }

  private suspend fun observePlayback() {
    var previous: MprisSnapshot? = null
    combine(
        PlaybackManager.state,
        PlaybackManager.currentItem,
        PlaybackManager.volume,
        repeatMode,
        shuffle,
      ) { state, item, volume, loop, shuffled ->
        MprisSnapshot(
          status = MprisMapper.playbackStatus(state),
          metadata = MprisMapper.metadata(item),
          volume = MprisMapper.volumeOf(volume),
          loopStatus = MprisMapper.loopStatusOf(loop),
          shuffle = shuffled,
        )
      }
      .collect { snapshot ->
        val changed = changedProperties(previous, snapshot)
        previous = snapshot
        if (changed.isNotEmpty()) {
          sendPropertiesChanged(changed)
        }
      }
  }

  private fun changedProperties(
    previous: MprisSnapshot?,
    snapshot: MprisSnapshot,
  ): HashMap<String, Variant<*>> {
    val changed = HashMap<String, Variant<*>>()
    if (previous == null) return changed
    if (previous.status != snapshot.status) {
      changed["PlaybackStatus"] = Variant(snapshot.status)
    }
    if (previous.metadata != snapshot.metadata) {
      changed["Metadata"] = Variant(snapshot.metadata, "a{sv}")
    }
    if (previous.volume != snapshot.volume) {
      changed["Volume"] = Variant(snapshot.volume)
    }
    if (previous.loopStatus != snapshot.loopStatus) {
      changed["LoopStatus"] = Variant(snapshot.loopStatus)
    }
    if (previous.shuffle != snapshot.shuffle) {
      changed["Shuffle"] = Variant(MprisMapper.shuffleToMpris(snapshot.shuffle))
    }
    return changed
  }

  private fun sendPropertiesChanged(changed: HashMap<String, Variant<*>>) {
    val conn = connection ?: return
    try {
      conn.sendMessage(
        Properties.PropertiesChanged(
          MprisMapper.OBJECT_PATH,
          MprisMapper.PLAYER_INTERFACE,
          changed,
          ArrayList(),
        )
      )
    } catch (_: Exception) {}
  }

  private data class MprisSnapshot(
    val status: String,
    val metadata: Map<String, Any>,
    val volume: Double,
    val loopStatus: String,
    val shuffle: Boolean,
  )

  private inner class MprisExporter : MprisRoot, MprisPlayer, Properties {

    override fun getObjectPath(): String = MprisMapper.OBJECT_PATH

    override fun Raise() = onRaise()

    override fun Quit() = onQuit()

    override fun Play() = PlaybackManager.play()

    override fun Pause() = PlaybackManager.pause()

    override fun PlayPause() = PlaybackManager.togglePlay()

    override fun Stop() {
      PlaybackManager.pause()
      PlaybackManager.seekTo(0)
    }

    override fun Next() = PlaybackManager.next()

    override fun Previous() = PlaybackManager.previous()

    override fun Seek(offset: Long) {
      val target = PlaybackManager.position.value + MprisMapper.microsToMs(offset)
      PlaybackManager.seekTo(target.coerceAtLeast(0))
    }

    override fun SetPosition(trackId: ObjectPath, position: Long) {
      val current = PlaybackManager.currentItem.value ?: return
      if (trackId.path != MprisMapper.trackIdPath(current.id)) return
      PlaybackManager.seekTo(MprisMapper.microsToMs(position).coerceAtLeast(0))
    }

    override fun OpenUri(uri: String) {
      if (!uri.startsWith("file://")) return
      val path = URLDecoder.decode(uri.removePrefix("file://"), StandardCharsets.UTF_8)
      PlaybackManager.playItem(
        PlaybackMediaItem(
          id = uri,
          title = path.substringAfterLast('/').ifEmpty { path },
          source = PlaybackMediaItem.Source.LocalFile(path),
        )
      )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <A> Get(iface: String?, property: String?): A {
      val value = GetAll(iface)[property] ?: throw UnknownProperty("Unknown property: $property")
      return value as A
    }

    override fun <A> Set(iface: String?, property: String?, value: A) {
      if (iface != MprisMapper.PLAYER_INTERFACE) {
        throw UnknownProperty("Unknown interface: $iface")
      }
      when (property) {
        "Volume" -> {
          val level = (value as? Number)?.toDouble() ?: return
          PlaybackManager.setVolume(MprisMapper.volumeLevel(level))
        }
        "LoopStatus" -> {
          val status = value as? String ?: return
          val mode = MprisMapper.repeatModeOf(status)
          repeatMode.value = mode
          PlaybackManager.setRepeatMode(mode)
        }
        "Shuffle" -> {
          val enabled = (value as? Boolean) ?: return
          val target = MprisMapper.shuffleFromMpris(enabled)
          shuffle.value = target
          PlaybackManager.setShuffle(target)
        }
        else -> throw PropertyReadOnly("Property $property is read-only")
      }
    }

    override fun GetAll(iface: String?): HashMap<String, Variant<*>> {
      val properties = HashMap<String, Variant<*>>()
      when (iface) {
        MprisMapper.ROOT_INTERFACE -> {
          properties["CanQuit"] = Variant(true)
          properties["CanRaise"] = Variant(true)
          properties["HasTrackList"] = Variant(false)
          properties["Identity"] = Variant(MprisMapper.IDENTITY)
          properties["SupportedUriSchemes"] = Variant(arrayListOf("file", "http", "https"), "as")
          properties["SupportedMimeTypes"] = Variant(arrayListOf<String>(), "as")
        }
        MprisMapper.PLAYER_INTERFACE -> {
          properties["PlaybackStatus"] =
            Variant(MprisMapper.playbackStatus(PlaybackManager.state.value))
          properties["LoopStatus"] = Variant(MprisMapper.loopStatusOf(repeatMode.value))
          properties["Rate"] = Variant(1.0)
          properties["Shuffle"] = Variant(MprisMapper.shuffleToMpris(shuffle.value))
          properties["Metadata"] =
            Variant(MprisMapper.metadata(PlaybackManager.currentItem.value), "a{sv}")
          properties["Volume"] = Variant(MprisMapper.volumeOf(PlaybackManager.volume.value))
          properties["Position"] = Variant(MprisMapper.msToMicros(PlaybackManager.position.value))
          properties["MinimumRate"] = Variant(1.0)
          properties["MaximumRate"] = Variant(1.0)
          properties["CanGoNext"] = Variant(true)
          properties["CanGoPrevious"] = Variant(true)
          properties["CanPlay"] = Variant(true)
          properties["CanPause"] = Variant(true)
          properties["CanSeek"] = Variant(true)
        }
        else -> throw UnknownProperty("Unknown interface: $iface")
      }
      return properties
    }
  }
}
