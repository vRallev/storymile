package software.ralf.storymile.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(entities = [ShowEntity::class, EpisodeEntity::class], version = 1)
@ConstructedBy(RoomPodcastDatabaseConstructor::class)
abstract class RoomPodcastDatabase : RoomDatabase() {
  abstract fun podcasts(): PodcastDao
}
