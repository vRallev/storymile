package software.ralf.storymile.libraryshows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class LibraryShowsPresenterImpl(private val database: PodcastDatabase) : LibraryShowsPresenter {
  @Composable
  override fun present(input: Unit): Model {
    var retry by remember { mutableIntStateOf(0) }
    val shows by
      produceState<Result<List<FollowedShow>>?>(initialValue = null, key1 = retry) {
        value = null
        try {
          database.podcasts.collect { podcasts ->
            value =
              Result.success(
                podcasts
                  .map { podcast ->
                    FollowedShow(
                      feedUrl = podcast.feedUrl,
                      title = podcast.title,
                      author = podcast.author,
                      artworkUrl = podcast.artworkUrl,
                    )
                  }
                  .sortedBy { it.title.lowercase() },
              )
          }
        } catch (exception: CancellationException) {
          throw exception
        } catch (exception: Exception) {
          value = Result.failure(exception)
        }
      }
    return Model(shows = shows, onRetry = { retry++ })
  }

  data class FollowedShow(
    val feedUrl: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
  )

  /** Renderer-only [shows] is null while loading. [onRetry] restarts collection after an error. */
  data class Model(val shows: Result<List<FollowedShow>>?, val onRetry: () -> Unit) :
    LibraryShowsPresenter.Model {
    override val isEmpty: Boolean
      get() = shows?.getOrNull()?.isEmpty() == true
  }
}
