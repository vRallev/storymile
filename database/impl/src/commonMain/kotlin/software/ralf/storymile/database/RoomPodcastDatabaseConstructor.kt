package software.ralf.storymile.database

import androidx.room3.RoomDatabaseConstructor

// Room generates the platform constructors.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RoomPodcastDatabaseConstructor : RoomDatabaseConstructor<RoomPodcastDatabase> {
  override fun initialize(): RoomPodcastDatabase
}
