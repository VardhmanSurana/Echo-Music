package echo.music.iad1tya.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation_exclusions")
data class RecommendationExclusionEntity(
  @PrimaryKey val trackKey: String,
  val excludedAtMillis: Long,
  val trackName: String = "",
  val artistName: String = "",
)
