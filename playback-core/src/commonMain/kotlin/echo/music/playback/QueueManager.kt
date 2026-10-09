package echo.music.playback

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RepeatMode {
  OFF,
  ONE,
  ALL,
}

class QueueManager(private val random: Random = Random.Default) {

  private val _queue = MutableStateFlow<List<PlaybackMediaItem>>(emptyList())
  val queue: StateFlow<List<PlaybackMediaItem>> = _queue.asStateFlow()

  private val _currentIndex = MutableStateFlow(-1)
  val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

  var repeatMode: RepeatMode = RepeatMode.OFF
    private set

  var isShuffled: Boolean = false
    private set

  private var originalOrder: List<PlaybackMediaItem> = emptyList()

  val currentItem: PlaybackMediaItem?
    get() = _queue.value.getOrNull(_currentIndex.value)

  fun setQueue(items: List<PlaybackMediaItem>, startIndex: Int = 0) {
    originalOrder = items
    val index = if (items.isEmpty()) -1 else startIndex.coerceIn(0, items.size - 1)
    _currentIndex.value = index
    _queue.value = if (isShuffled) shuffledKeeping(items, index) else items
  }

  fun playNext(): PlaybackMediaItem? {
    val list = _queue.value
    val index = _currentIndex.value
    if (list.isEmpty() || index < 0) return null
    if (index < list.size - 1) {
      _currentIndex.value = index + 1
      return list[index + 1]
    }
    return when (repeatMode) {
      RepeatMode.ALL -> {
        _currentIndex.value = 0
        list[0]
      }
      RepeatMode.ONE -> list[index]
      RepeatMode.OFF -> null
    }
  }

  fun playPrevious(): PlaybackMediaItem? {
    val list = _queue.value
    val index = _currentIndex.value
    if (list.isEmpty() || index < 0) return null
    if (index > 0) {
      _currentIndex.value = index - 1
      return list[index - 1]
    }
    return if (repeatMode == RepeatMode.ALL) {
      _currentIndex.value = list.size - 1
      list.last()
    } else {
      list[index]
    }
  }

  fun addNext(item: PlaybackMediaItem) {
    val list = _queue.value.toMutableList()
    val insertAt = (_currentIndex.value + 1).coerceIn(0, list.size)
    list.add(insertAt, item)
    _queue.value = list
    originalOrder = originalOrder + item
  }

  fun addToQueue(item: PlaybackMediaItem) {
    _queue.value = _queue.value + item
    originalOrder = originalOrder + item
  }

  fun removeAt(index: Int) {
    val list = _queue.value.toMutableList()
    if (index !in list.indices) return
    val removed = list.removeAt(index)
    originalOrder = originalOrder.filterNot { it === removed }
    _queue.value = list
    when {
      list.isEmpty() -> _currentIndex.value = -1
      index < _currentIndex.value -> _currentIndex.value -= 1
      index == _currentIndex.value && index >= list.size -> _currentIndex.value = list.size - 1
    }
  }

  fun move(from: Int, to: Int) {
    val list = _queue.value.toMutableList()
    if (from !in list.indices || to !in list.indices) return
    val item = list.removeAt(from)
    list.add(to, item)
    _queue.value = list
    if (!isShuffled) {
      originalOrder = list.toList()
    }
    val current = _currentIndex.value
    _currentIndex.value =
      when {
        current == from -> to
        from < to && current in (from + 1)..to -> current - 1
        from > to && current in to until from -> current + 1
        else -> current
      }
  }

  fun jumpTo(index: Int) {
    if (index in _queue.value.indices) {
      _currentIndex.value = index
    }
  }

  fun setShuffle(enabled: Boolean) {
    if (enabled == isShuffled) return
    isShuffled = enabled
    val current = currentItem
    if (enabled) {
      _queue.value = shuffledKeeping(_queue.value, _currentIndex.value)
    } else {
      val restored = originalOrder
      _queue.value = restored
      _currentIndex.value = current?.let { c -> restored.indexOfFirst { it.id == c.id } } ?: -1
    }
  }

  fun setRepeatMode(mode: RepeatMode) {
    repeatMode = mode
  }

  fun updateCurrentItem(item: PlaybackMediaItem) {
    val index = _currentIndex.value
    if (index in _queue.value.indices) {
      val updated = _queue.value.toMutableList()
      updated[index] = item
      _queue.value = updated
    }
  }

  private fun shuffledKeeping(
    items: List<PlaybackMediaItem>,
    currentIndex: Int,
  ): List<PlaybackMediaItem> {
    val rest = items.toMutableList()
    if (currentIndex in rest.indices) {
      rest.removeAt(currentIndex)
    }
    shuffleList(rest)
    if (currentIndex in items.indices) {
      rest.add(currentIndex, items[currentIndex])
    }
    return rest
  }

  private fun shuffleList(list: MutableList<PlaybackMediaItem>) {
    for (i in list.indices.reversed()) {
      val j = random.nextInt(i + 1)
      val tmp = list[i]
      list[i] = list[j]
      list[j] = tmp
    }
  }
}
