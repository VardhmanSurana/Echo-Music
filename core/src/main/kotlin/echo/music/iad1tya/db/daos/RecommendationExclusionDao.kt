package echo.music.iad1tya.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import echo.music.iad1tya.db.entities.RecommendationExclusionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationExclusionDao {
  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(entity: RecommendationExclusionEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(entities: List<RecommendationExclusionEntity>)

  @Query("SELECT * FROM recommendation_exclusions ORDER BY excludedAtMillis DESC")
  suspend fun getAll(): List<RecommendationExclusionEntity>

  @Query("SELECT * FROM recommendation_exclusions ORDER BY excludedAtMillis DESC")
  fun observeAll(): Flow<List<RecommendationExclusionEntity>>

  @Query("SELECT COUNT(*) FROM recommendation_exclusions") suspend fun count(): Int

  @Query("DELETE FROM recommendation_exclusions") suspend fun clear()

  @Query("DELETE FROM recommendation_exclusions WHERE trackKey = :trackKey")
  suspend fun delete(trackKey: String)
}
