package software.ralf.storymile.libraryshows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.storymile.podcast.SavedShowRepository
import software.ralf.storymile.user.UserScope

@Inject
@ContributesBinding(UserScope::class)
class LibraryShowsPresenterImpl(private val savedShows: SavedShowRepository) :
  LibraryShowsPresenter {
  @Composable
  override fun present(input: Unit): Model {
    val podcasts by savedShows.shows.collectAsState()
    val shows =
      remember(podcasts) {
        podcasts
          ?.map { podcast ->
            FollowedShow(
              feedUrl = podcast.feedUrl,
              title = podcast.title,
              author = podcast.author,
              artworkUrl = podcast.artworkUrl,
            )
          }
          ?.sortedBy { it.title.lowercase() }
      }
    return Model(shows)
  }

  data class FollowedShow(
    val feedUrl: String,
    val title: String,
    val author: String?,
    val artworkUrl: String?,
  )

  /** Renderer-only [shows] is null while loading. */
  data class Model(val shows: List<FollowedShow>?) : LibraryShowsPresenter.Model {
    override val isEmpty: Boolean
      get() = shows?.isEmpty() == true
  }
}
