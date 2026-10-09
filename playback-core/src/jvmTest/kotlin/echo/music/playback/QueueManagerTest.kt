package echo.music.playback

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QueueManagerTest {

  private fun item(id: String) =
    PlaybackMediaItem(
      id = id,
      title = id,
      artist = "artist",
      album = "album",
      artworkUrl = null,
      durationMs = 1_000L,
      source = PlaybackMediaItem.Source.LocalFile("/music/$id.mp3"),
    )

  @Test
  fun addRemoveMoveAndJumpTo() {
    val queue = QueueManager()
    queue.setQueue(listOf(item("a"), item("b"), item("c")), 0)

    queue.addNext(item("d"))
    assertEquals(listOf("a", "d", "b", "c"), queue.queue.value.map { it.id })
    assertEquals(0, queue.currentIndex.value)

    queue.addToQueue(item("e"))
    assertEquals(listOf("a", "d", "b", "c", "e"), queue.queue.value.map { it.id })

    queue.removeAt(1)
    assertEquals(listOf("a", "b", "c", "e"), queue.queue.value.map { it.id })

    queue.move(0, 2)
    assertEquals(listOf("b", "c", "a", "e"), queue.queue.value.map { it.id })
    assertEquals(2, queue.currentIndex.value)
    assertEquals("a", queue.currentItem?.id)

    queue.jumpTo(1)
    assertEquals(1, queue.currentIndex.value)
    assertEquals("c", queue.currentItem?.id)

    queue.jumpTo(99)
    assertEquals(1, queue.currentIndex.value)
  }

  @Test
  fun removingCurrentItemKeepsIndexValid() {
    val queue = QueueManager()
    queue.setQueue(listOf(item("a"), item("b"), item("c")), 2)
    queue.removeAt(2)
    assertEquals(1, queue.currentIndex.value)
    assertEquals("b", queue.currentItem?.id)

    queue.removeAt(0)
    queue.removeAt(0)
    assertEquals(-1, queue.currentIndex.value)
    assertNull(queue.currentItem)
    assertNull(queue.playNext())
    assertNull(queue.playPrevious())
  }

  @Test
  fun navigationWithRepeatOffStopsAtEnd() {
    val queue = QueueManager()
    queue.setQueue(listOf(item("a"), item("b")), 0)

    assertEquals("b", queue.playNext()?.id)
    assertNull(queue.playNext())
    assertEquals(1, queue.currentIndex.value)

    assertEquals("a", queue.playPrevious()?.id)
    assertEquals("a", queue.playPrevious()?.id)
    assertEquals(0, queue.currentIndex.value)
  }

  @Test
  fun repeatAllWrapsAround() {
    val queue = QueueManager()
    queue.setQueue(listOf(item("a"), item("b")), 1)
    queue.setRepeatMode(RepeatMode.ALL)

    assertEquals("a", queue.playNext()?.id)
    assertEquals(0, queue.currentIndex.value)

    assertEquals("b", queue.playPrevious()?.id)
    assertEquals(1, queue.currentIndex.value)

    queue.jumpTo(0)
    assertEquals("b", queue.playPrevious()?.id)
  }

  @Test
  fun repeatOneRepeatsCurrent() {
    val queue = QueueManager()
    queue.setQueue(listOf(item("a"), item("b")), 1)
    queue.setRepeatMode(RepeatMode.ONE)

    assertEquals("b", queue.playNext()?.id)
    assertEquals(1, queue.currentIndex.value)
  }

  @Test
  fun shuffleKeepsAllItemsAndChangesOrder() {
    val items = (1..20).map { item("t$it") }
    val queue = QueueManager(Random(7L))
    queue.setQueue(items, 0)
    queue.setShuffle(true)

    assertEquals(items.map { it.id }.toSet(), queue.queue.value.map { it.id }.toSet())
    assertEquals(items.size, queue.queue.value.size)
    assertTrue(items.map { it.id } != queue.queue.value.map { it.id })
    assertEquals("t1", queue.currentItem?.id)

    queue.jumpTo(3)
    val currentId = queue.currentItem?.id
    queue.setShuffle(false)
    assertEquals(items.map { it.id }, queue.queue.value.map { it.id })
    assertEquals(items.indexOfFirst { it.id == currentId }, queue.currentIndex.value)
    assertEquals(currentId, queue.currentItem?.id)
  }
}
