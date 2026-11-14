package software.ralf.storymile.database

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
  entities = [ShowEntity::class, EpisodeEntity::class, QueuedEpisodeEntity::class],
  version = 2,
  autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@ConstructedBy(RoomPodcastDatabaseConstructor::class)
abstract class RoomPodcastDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao

  abstract fun queue(): EpisodeQueueDao
}
