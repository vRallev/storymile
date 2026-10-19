package software.ralf.storymile.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.register
import software.ralf.storymile.podcast.PodcastEnclosure
import software.ralf.storymile.podcast.PodcastEpisode
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.Storage

class DatabaseFixture(path: String? = null) {
  // Room connection timeouts must use real time while filesystem or browser-worker I/O is pending.
  private val owner =
    CoroutineScopeScoped(Dispatchers.Default + SupervisorJob() + CoroutineName("database-test"))
  private val scope = Scope.buildRootScope("database-test") { addCoroutineScopeScoped(owner) }

  val database: PodcastDatabase =
    UserPodcastDatabase(
        storage = DatabaseStorage(path),
        coroutineScope = owner.createChild(),
        platform = PlatformDatabase(),
      )
      .also { scope.register(it) }

  suspend fun close() {
    if (!scope.isDestroyed()) {
      scope.destroy()
    }
    owner.coroutineContext.job.join()
  }

  suspend fun <T> use(block: suspend (PodcastDatabase) -> T): T {
    return try {
      block(database)
    } finally {
      close()
    }
  }

  companion object {
    fun podcast(): PodcastFeed =
      PodcastFeed(
        feedUrl = "https://feeds.test/show.xml",
        title = "A show",
        description = "<p>Show metadata</p>",
        author = "Publisher",
        artworkUrl = "https://images.test/show.png",
        link = "https://shows.test/show",
        episodes =
          listOf(
            PodcastEpisode(
              id = "second",
              title = "Latest episode",
              description = "<p>Episode metadata</p>",
              author = "Episode author",
              artworkUrl = "https://images.test/episode.png",
              publishedAt = "Tue, 20 Oct 2026 12:00:00 GMT",
              duration = 123_456.milliseconds,
              enclosure =
                PodcastEnclosure(
                  url = "https://media.test/second.mp3",
                  type = "audio/mpeg",
                  length = 6_000_000_000L,
                ),
            ),
            PodcastEpisode(
              id = "first",
              title = "First episode",
              description = null,
              author = null,
              artworkUrl = null,
              publishedAt = null,
              duration = null,
              enclosure =
                PodcastEnclosure(url = "https://media.test/first.mp3", type = null, length = null),
            ),
          ),
      )
  }

  private class DatabaseStorage(private val path: String?) : Storage {
    override val files: FileStorage
      get() = error("Not used")

    override val cache: FileStorage
      get() = error("Not used")

    override suspend fun databasePath(name: String): String? = path

    override suspend fun preferences(name: String): DataStore<Preferences> = error("Not used")
  }
}
