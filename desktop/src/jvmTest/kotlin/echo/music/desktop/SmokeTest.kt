package echo.music.desktop

import com.music.innertube.YouTube
import kotlin.test.Test
import kotlin.test.assertNotNull

class SmokeTest {
  @Test
  fun sharedInnertubeSourcesAreOnClasspath() {
    assertNotNull(YouTube::class)
  }
}
