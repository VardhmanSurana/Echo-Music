package echo.music.iad1tya.db

import java.sql.Connection
import java.sql.DriverManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the v46 → v47 backfill SQL against a real SQLite engine.
 *
 * The fixture tables are a minimal projection of the v46 schema: only the columns
 * `MIGRATION_46_47_BACKFILL_SQL` reads or writes, each with the type and nullability it has in
 * `core/schemas/echo.music.iad1tya.db.InternalDatabase/46.json`. They are deliberately not the full
 * table definitions (no foreign keys, no indices, no unrelated columns). A column the SQL needs but
 * the fixture omits makes the statement fail outright, so a missing column cannot pass silently.
 */
class Migration46To47BackfillTest {
  private lateinit var db: Connection

  private data class StatsRow(
    val trackKey: String,
    val title: String,
    val artist: String,
    val videoId: String?,
    val artworkUrl: String?,
    val totalPlayTimeMs: Long,
    val playCount: Int,
    val skipCount: Int,
    val lastPlayedAtMillis: Long,
  )

  @Before
  fun setUp() {
    db = DriverManager.getConnection("jdbc:sqlite::memory:")
    exec(SONG_DDL)
    exec(ARTIST_DDL)
    exec(SONG_ARTIST_MAP_DDL)
    exec(EVENT_DDL)
    exec(SONG_PLAY_STATS_DDL)
    insertFixtures()
    exec(MIGRATION_46_47_BACKFILL_SQL)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun duplicateUploadsOfSameRecordingCollapseIntoOneRow() {
    val hello = rows().filter { it.trackKey == "hello world|first artist" }
    assertEquals(1, hello.size)
    // "Never Played" has no events and "Zero Play Song" has only non-positive ones: neither seeds a
    // row. v6 (different artist) and v7 (no artist) seed their own rows, so four rows in total.
    assertEquals(4, rows().size)
    val representative = hello.single()
    assertTrue(representative.videoId == "v1" || representative.videoId == "v2")
    assertEquals("Hello World", representative.title.trim())
    assertTrue(
      representative.artworkUrl == "http://img/v1.jpg" ||
        representative.artworkUrl == "http://img/v2.jpg",
    )
  }

  @Test
  fun totalPlayTimeAndPlayCountAggregateAcrossConsolidatedUploads() {
    val hello = row("hello world|first artist")
    assertEquals(10_000L + 7_000L, hello.totalPlayTimeMs)
    assertEquals(2, hello.playCount)
    assertEquals(0, hello.skipCount)
  }

  @Test
  fun lastPlayedAtMillisIsEpochMilliSecondsOfLatestQualifyingEvent() {
    val hello = row("hello world|first artist")
    // The newest event for this track has playTime -500, so a correct backfill ignores it.
    assertEquals(1_700_000_500_000L, hello.lastPlayedAtMillis)
    assertEquals(
      1_700_000_900_000L,
      row("duet song|first artist, second artist").lastPlayedAtMillis
    )
  }

  @Test
  fun multiArtistTrackConcatenatesArtistsInCommaSeparatedForm() {
    val duet = row("duet song|first artist, second artist")
    assertEquals("First Artist, Second Artist", duet.artist)
    assertEquals(3_000L + 2_000L, duet.totalPlayTimeMs)
    assertEquals(2, duet.playCount)
    assertEquals(0, duet.skipCount)
    assertEquals("Duet Song", duet.title)
  }

  @Test
  fun sameTitleWithDifferentArtistsSeedsDistinctRows() {
    val helloRows = rows().filter { it.trackKey.startsWith("hello world|") }
    assertEquals(2, helloRows.size)
    val second = row("hello world|second artist")
    assertEquals("Second Artist", second.artist)
    assertEquals(4_000L, second.totalPlayTimeMs)
    assertEquals(1, second.playCount)
    assertEquals(1_700_000_950_000L, second.lastPlayedAtMillis)
  }

  @Test
  fun songWithNoArtistMappingSeedsEmptyArtistRow() {
    val lonely = row("lonely track|")
    assertEquals("", lonely.artist)
    assertEquals("v7", lonely.videoId)
    assertEquals(6_000L, lonely.totalPlayTimeMs)
    assertEquals(1, lonely.playCount)
  }

  @Test
  fun eventsWithNonPositivePlayTimeProduceNoRowsAndNoCounts() {
    val keys = rows().map { it.trackKey }
    assertFalse("zero-play song must not be seeded", keys.contains("zero play song|first artist"))
    assertFalse(
      "song without events must not be seeded",
      keys.contains("never played|first artist")
    )
    // The two discarded v1 events contribute no play time, no count, and no later timestamp.
    val hello = row("hello world|first artist")
    assertEquals(17_000L, hello.totalPlayTimeMs)
    assertEquals(2, hello.playCount)
    assertEquals(1_700_000_500_000L, hello.lastPlayedAtMillis)
  }

  private fun row(trackKey: String): StatsRow =
    rows().firstOrNull { it.trackKey == trackKey }
      ?: throw AssertionError(
        "no row for trackKey=$trackKey, present=${rows().map { it.trackKey }}",
      )

  private fun rows(): List<StatsRow> {
    db.createStatement().use { statement ->
      statement.executeQuery("SELECT * FROM song_play_stats ORDER BY trackKey").use { rs ->
        val result = mutableListOf<StatsRow>()
        while (rs.next()) {
          result.add(
            StatsRow(
              trackKey = rs.getString("trackKey"),
              title = rs.getString("title"),
              artist = rs.getString("artist"),
              videoId = rs.getString("videoId"),
              artworkUrl = rs.getString("artworkUrl"),
              totalPlayTimeMs = rs.getLong("totalPlayTimeMs"),
              playCount = rs.getInt("playCount"),
              skipCount = rs.getInt("skipCount"),
              lastPlayedAtMillis = rs.getLong("lastPlayedAtMillis"),
            ),
          )
        }
        return result
      }
    }
  }

  private fun exec(sql: String) {
    db.createStatement().use { it.execute(sql) }
  }

  private fun insertFixtures() {
    // sqlite-jdbc runs only the first statement of a multi-statement string, so each batch is
    // separate.
    listOf(SONG_FIXTURES, ARTIST_FIXTURES, SONG_ARTIST_MAP_FIXTURES, EVENT_FIXTURES).forEach(::exec)
  }

  private companion object {
    const val SONG_DDL =
      "CREATE TABLE song (id TEXT NOT NULL, title TEXT NOT NULL, thumbnailUrl TEXT, PRIMARY KEY(id))"

    const val ARTIST_DDL =
      "CREATE TABLE artist (id TEXT NOT NULL, name TEXT NOT NULL, PRIMARY KEY(id))"

    const val SONG_ARTIST_MAP_DDL =
      "CREATE TABLE song_artist_map (songId TEXT NOT NULL, artistId TEXT NOT NULL, " +
        "position INTEGER NOT NULL, PRIMARY KEY(songId, artistId))"

    const val EVENT_DDL =
      "CREATE TABLE event (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, songId TEXT NOT NULL, " +
        "timestamp INTEGER NOT NULL, playTime INTEGER NOT NULL)"

    const val SONG_PLAY_STATS_DDL =
      "CREATE TABLE song_play_stats (trackKey TEXT NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, " +
        "videoId TEXT, artworkUrl TEXT, totalPlayTimeMs INTEGER NOT NULL, playCount INTEGER NOT NULL, " +
        "skipCount INTEGER NOT NULL, lastPlayedAtMillis INTEGER NOT NULL, PRIMARY KEY(trackKey))"

    val SONG_FIXTURES =
      """
      INSERT INTO song (id, title, thumbnailUrl) VALUES
        ('v1', '  Hello World  ', 'http://img/v1.jpg'),
        ('v2', 'Hello World', 'http://img/v2.jpg'),
        ('v3', 'Duet Song', 'http://img/v3.jpg'),
        ('v4', 'Never Played', 'http://img/v4.jpg'),
        ('v5', 'Zero Play Song', 'http://img/v5.jpg'),
        ('v6', 'Hello World', 'http://img/v6.jpg'),
        ('v7', 'Lonely Track', 'http://img/v7.jpg');
      """
        .trimIndent()

    val ARTIST_FIXTURES =
      """
      INSERT INTO artist (id, name) VALUES
        ('a1', 'First Artist'),
        ('a2', 'Second Artist');
      """
        .trimIndent()

    val SONG_ARTIST_MAP_FIXTURES =
      """
      INSERT INTO song_artist_map (songId, artistId, position) VALUES
        ('v1', 'a1', 0),
        ('v2', 'a1', 0),
        ('v3', 'a1', 0),
        ('v3', 'a2', 1),
        ('v4', 'a1', 0),
        ('v5', 'a1', 0),
        ('v6', 'a2', 0);
      """
        .trimIndent()

    val EVENT_FIXTURES =
      """
      INSERT INTO event (songId, timestamp, playTime) VALUES
        ('v1', 1700000000000, 10000),
        ('v2', 1700000500000, 7000),
        ('v3', 1700000900000, 3000),
        ('v3', 1700000800000, 2000),
        ('v1', 1700000400000, 0),
        ('v1', 1700000600000, -500),
        ('v5', 1700000700000, 0),
        ('v5', 1700000750000, -1),
        ('v6', 1700000950000, 4000),
        ('v7', 1700000960000, 6000);
      """
        .trimIndent()
  }
}
