package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class SavedShowRepositoryImpl(
  private val database: PodcastDatabase,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : SavedShowRepository {
  private val changes =
    UserDatabaseChanges(
      data = database.podcasts,
      coroutineScope = coroutineScope,
      readError = "Could not load followed shows.",
      writeError = "Could not update a followed show.",
    )

  override val shows: StateFlow<List<PodcastFeed>?> = changes.state

  override fun save(show: PodcastFeed) {
    changes.submit { database.insert(show) }
  }

  override fun remove(feedUrl: String) {
    changes.submit { database.delete(feedUrl) }
  }
}
