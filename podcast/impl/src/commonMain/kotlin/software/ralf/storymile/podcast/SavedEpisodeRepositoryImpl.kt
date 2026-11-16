package software.ralf.storymile.podcast

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.database.SavedEpisodeDatabase
import software.ralf.storymile.user.UserScope

@Inject
@SingleIn(UserScope::class)
@ContributesBinding(UserScope::class)
class SavedEpisodeRepositoryImpl(
  private val database: SavedEpisodeDatabase,
  @ForScope(UserScope::class) coroutineScope: CoroutineScope,
) : SavedEpisodeRepository {
  private val changes =
    UserDatabaseChanges(
      data = database.savedEpisodes,
      coroutineScope = coroutineScope,
      readError = "Could not load saved episodes.",
      writeError = "Could not update saved episodes.",
    )

  override val episodes: StateFlow<List<SavedEpisode>?> = changes.state

  override fun save(episode: SavedEpisode) {
    changes.submit { database.save(episode) }
  }

  override fun remove(episode: SavedEpisode) {
    changes.submit {
      database.removeSavedEpisode(feedUrl = episode.feedUrl, episodeId = episode.episode.id)
    }
  }
}
