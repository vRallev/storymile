package software.ralf.storymile.database

import kotlin.coroutines.CoroutineContext

interface DatabasePlatform {
  suspend fun open(path: String?, coroutineContext: CoroutineContext): RoomPodcastDatabase

  suspend fun close(database: RoomPodcastDatabase)
}
