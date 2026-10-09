package echo.music.playback

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URL
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FallbackAudioEngine : PlaybackEngine {

  private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
  override val state: StateFlow<PlaybackState> = _state.asStateFlow()

  private val _currentPosition = MutableStateFlow(0L)
  override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

  private val _duration = MutableStateFlow(0L)
  override val duration: StateFlow<Long> = _duration.asStateFlow()

  private val _volume = MutableStateFlow(1.0f)
  override val volume: StateFlow<Float> = _volume.asStateFlow()

  private var source: SourceSpec? = null

  private var worker: PlaybackWorker? = null

  override suspend fun loadFile(path: String, startPositionMs: Long) {
    load(FileSource(path), startPositionMs)
  }

  override suspend fun loadStream(
    url: String,
    headers: Map<String, String>,
    startPositionMs: Long,
  ) {
    load(StreamSource(url, headers), startPositionMs)
  }

  override fun play() {
    val current = worker
    if (current != null) {
      current.paused = false
      if (_state.value == PlaybackState.Paused) {
        _state.value = PlaybackState.Playing
      }
    } else if (source != null) {
      restartWorker(_currentPosition.value, startPaused = false)
    }
  }

  override fun pause() {
    worker?.paused = true
    if (_state.value == PlaybackState.Playing) {
      _state.value = PlaybackState.Paused
    }
  }

  override fun seekTo(positionMs: Long) {
    _currentPosition.value = positionMs
    worker?.requestSeek(positionMs)
  }

  override fun setVolume(volume: Float) {
    val clamped = volume.coerceIn(0f, 1f)
    _volume.value = clamped
    val gain = if (clamped <= 0f) MIN_GAIN_DB else (20.0 * log10(clamped.toDouble())).toFloat()
    worker?.requestGain(gain)
  }

  override fun setOutputDevice(device: AudioOutputDevice) {}

  override fun release() {
    val current = worker
    worker = null
    current?.quit()
    _state.value = PlaybackState.Stopped
  }

  private fun load(spec: SourceSpec, startPositionMs: Long) {
    source = spec
    _state.value = PlaybackState.Loading
    _currentPosition.value = startPositionMs
    _duration.value = 0L
    restartWorker(startPositionMs, startPaused = true)
  }

  private fun restartWorker(startMs: Long, startPaused: Boolean) {
    val previous = worker
    worker = null
    previous?.quit()
    val spec = source ?: return
    val next = PlaybackWorker(spec, startMs, startPaused)
    worker = next
    next.start()
  }

  private fun openInput(spec: SourceSpec, startMs: Long): DecodedInput? =
    when (spec) {
      is FileSource -> openFile(spec.path, startMs)
      is StreamSource -> openStream(spec, startMs)
    }

  private fun openFile(path: String, startMs: Long): DecodedInput? {
    try {
      val raw = AudioSystem.getAudioInputStream(File(path))
      val durationMs =
        if (raw.frameLength > 0 && raw.format.frameRate > 0) {
          raw.frameLength * 1000L / raw.format.frameRate.toLong()
        } else {
          null
        }
      val converted = toPcm(raw)
      skipFully(converted, msToBytes(startMs, converted.format))
      return DecodedInput(converted, converted.format, durationMs)
    } catch (_: Throwable) {}
    return openFfmpegFile(path, startMs)
  }

  private fun openFfmpegFile(path: String, startMs: Long): DecodedInput? {
    return try {
      val command = mutableListOf("ffmpeg", "-nostdin", "-v", "error")
      if (startMs > 0) {
        command += listOf("-ss", (startMs / 1000.0).toString())
      }
      command += listOf("-i", path, "-f", "s16le", "-ac", "2", "-ar", "44100", "-")
      val process = ProcessBuilder(command).start()
      val pcmFormat = AudioFormat(PCM_SAMPLE_RATE, 16, 2, true, false)
      DecodedInput(process.inputStream, pcmFormat, null, process::destroy)
    } catch (_: Throwable) {
      null
    }
  }

  private fun openStream(spec: StreamSource, startMs: Long): DecodedInput? {
    try {
      val raw = AudioSystem.getAudioInputStream(URL(spec.url))
      val converted = toPcm(raw)
      skipFully(converted, msToBytes(startMs, converted.format))
      return DecodedInput(converted, converted.format, null)
    } catch (t: Throwable) {}
    return openFfmpeg(spec, startMs)
  }

  private fun openFfmpeg(spec: StreamSource, startMs: Long): DecodedInput? {
    return try {
      val command = mutableListOf("ffmpeg", "-nostdin", "-v", "error")
      if (startMs > 0) {
        command += listOf("-ss", (startMs / 1000.0).toString())
      }
      if (spec.headers.isNotEmpty()) {
        val headerBlock = spec.headers.entries.joinToString("\r\n") { "${it.key}: ${it.value}" }
        command += listOf("-headers", headerBlock + "\r\n")
      }
      command += listOf("-i", spec.url, "-f", "s16le", "-ac", "2", "-ar", "44100", "-")
      val process = ProcessBuilder(command).start()
      val pcmFormat = AudioFormat(PCM_SAMPLE_RATE, 16, 2, true, false)
      DecodedInput(process.inputStream, pcmFormat, null, process::destroy)
    } catch (t: Throwable) {
      null
    }
  }

  private fun toPcm(stream: AudioInputStream): AudioInputStream {
    val sourceFormat = stream.format
    val target =
      AudioFormat(
        AudioFormat.Encoding.PCM_SIGNED,
        sourceFormat.sampleRate,
        16,
        sourceFormat.channels,
        sourceFormat.channels * 2,
        sourceFormat.sampleRate,
        false,
      )
    return AudioSystem.getAudioInputStream(target, stream)
  }

  private fun msToBytes(ms: Long, format: AudioFormat): Long {
    if (ms <= 0) {
      return 0L
    }
    val bytesPerSecond = format.frameRate * format.frameSize
    return (ms / 1000.0 * bytesPerSecond).toLong()
  }

  private fun skipFully(stream: InputStream, bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
      val skipped = stream.skip(remaining)
      if (skipped > 0) {
        remaining -= skipped
        continue
      }
      if (stream.read() < 0) {
        return
      }
      remaining -= 1
    }
  }

  private inner class PlaybackWorker(
    private val spec: SourceSpec,
    private val startMs: Long,
    startPaused: Boolean,
  ) : Thread("fallback-audio-worker") {

    @Volatile private var quitFlag = false

    @Volatile var paused = startPaused

    @Volatile private var seekRequestMs: Long? = null

    @Volatile private var gainRequestDb: Float? = null

    @Volatile private var activeStream: InputStream? = null

    @Volatile private var activeLine: SourceDataLine? = null

    init {
      isDaemon = true
    }

    fun quit() {
      quitFlag = true
      try {
        activeStream?.close()
      } catch (_: IOException) {}
      try {
        activeLine?.close()
      } catch (_: Throwable) {}
      interrupt()
    }

    fun requestSeek(positionMs: Long) {
      seekRequestMs = positionMs
    }

    fun requestGain(gainDb: Float) {
      gainRequestDb = gainDb
    }

    override fun run() {
      var input: DecodedInput? = null
      var line: SourceDataLine? = null
      try {
        val opened =
          openInput(spec, startMs)
            ?: run {
              _state.value = PlaybackState.Failed("No decoder available")
              return
            }
        input = opened
        activeStream = opened.stream
        opened.durationMs?.let {
          _duration.value = it
        }
        val dataLine =
          AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, opened.format))
            as SourceDataLine
        dataLine.open(opened.format)
        line = dataLine
        activeLine = dataLine
        applyPendingGain(dataLine)
        var current: DecodedInput = opened
        var baseMs = startMs
        var framesSinceOpen = 0L
        if (!paused) {
          dataLine.start()
          _state.value = PlaybackState.Playing
        } else {
          _state.value = PlaybackState.Paused
        }
        val frameSize = opened.format.frameSize.coerceAtLeast(1)
        val sampleRate = opened.format.sampleRate.toDouble().coerceAtLeast(1.0)
        val buffer = ByteArray(maxOf(8192, frameSize * 512))
        while (!quitFlag) {
          val seekTarget = seekRequestMs
          if (seekTarget != null) {
            seekRequestMs = null
            dataLine.stop()
            dataLine.flush()
            val reopened = openInput(spec, seekTarget)
            if (reopened == null) {
              _state.value = PlaybackState.Failed("No decoder available")
              return
            }
            try {
              current.stream.close()
            } catch (_: IOException) {}
            try {
              current.close()
            } catch (_: IOException) {}
            current = reopened
            input = reopened
            activeStream = reopened.stream
            baseMs = seekTarget
            framesSinceOpen = 0L
            _currentPosition.value = seekTarget
            reopened.durationMs?.let {
              _duration.value = it
            }
            if (!paused) {
              dataLine.start()
            }
          }
          applyPendingGain(dataLine)
          if (paused) {
            dataLine.stop()
            try {
              sleep(PAUSED_POLL_INTERVAL_MS)
            } catch (ie: InterruptedException) {
              if (quitFlag) {
                return
              }
            }
            continue
          }
          val count = current.stream.read(buffer)
          if (count < 0) {
            dataLine.drain()
            _state.value = PlaybackState.Stopped
            return
          }
          dataLine.start()
          dataLine.write(buffer, 0, count)
          framesSinceOpen += count / frameSize
          val bufferedFrames = dataLine.available().coerceAtLeast(0) / frameSize
          val playedMs = ((framesSinceOpen - bufferedFrames) * 1000.0 / sampleRate).toLong()
          _currentPosition.value = baseMs + playedMs
        }
        _state.value = PlaybackState.Stopped
      } catch (t: Throwable) {
        if (!quitFlag) {
          _state.value = PlaybackState.Failed(t.message ?: "Playback failed")
        }
      } finally {
        try {
          input?.stream?.close()
        } catch (_: IOException) {}
        try {
          input?.close()
        } catch (_: IOException) {}
        try {
          line?.close()
        } catch (_: Throwable) {}
        activeStream = null
        activeLine = null
      }
    }

    private fun applyPendingGain(line: SourceDataLine) {
      val gainDb = gainRequestDb ?: return
      gainRequestDb = null
      try {
        val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
        control.value = gainDb.coerceIn(control.minimum, control.maximum)
      } catch (_: Throwable) {}
    }
  }

  private sealed interface SourceSpec

  private data class FileSource(val path: String) : SourceSpec

  private data class StreamSource(
    val url: String,
    val headers: Map<String, String>,
  ) : SourceSpec

  private class DecodedInput(
    val stream: InputStream,
    val format: AudioFormat,
    val durationMs: Long?,
    val onClose: () -> Unit = {},
  ) {
    fun close() {
      onClose()
    }
  }

  companion object {
    private const val MIN_GAIN_DB = -80f

    private const val PCM_SAMPLE_RATE = 44100f

    private const val PAUSED_POLL_INTERVAL_MS = 20L
  }
}
