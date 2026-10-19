package software.ralf.storymile.database

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.coroutines.CoroutineContext
import software.ralf.storymile.user.UserScope

@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class PlatformDatabase : DatabasePlatform {
  override suspend fun open(
    path: String?,
    coroutineContext: CoroutineContext,
  ): RoomPodcastDatabase {
    val builder =
      if (path == null) {
        Room.inMemoryDatabaseBuilder<RoomPodcastDatabase> {
          RoomPodcastDatabaseConstructor.initialize()
        }
      } else {
        Room.databaseBuilder<RoomPodcastDatabase>(path) {
          RoomPodcastDatabaseConstructor.initialize()
        }
      }
    return builder
      .setDriver(BundledSQLiteDriver())
      .setQueryCoroutineContext(coroutineContext)
      .build()
  }

  override suspend fun close(database: RoomPodcastDatabase) {
    database.close()
  }
}
