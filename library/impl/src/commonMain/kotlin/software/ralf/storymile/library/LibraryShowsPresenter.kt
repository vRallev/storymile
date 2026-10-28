package software.ralf.storymile.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.shows.ShowDetailsModel
import software.ralf.storymile.shows.ShowEpisodes
import software.ralf.storymile.util.HtmlDescription

@Inject
class LibraryShowsPresenter(
  private val database: PodcastDatabase,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
) : ComposePresenter<Unit, LibraryShowsPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    var retry by remember { mutableIntStateOf(0) }
    val saved = savedShows(retry)
    val feeds = saved?.getOrNull().orEmpty()
    var selectedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var detailOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(feeds, saved) {
      if (saved?.isSuccess == true && feeds.isEmpty()) {
        selectedUrl = null
        detailOpen = false
      }
    }
    val selected = feeds.firstOrNull { it.feedUrl == selectedUrl } ?: feeds.firstOrNull()
    var descriptionExpanded by rememberSaveable(selected?.feedUrl) { mutableStateOf(false) }
    var updating by remember { mutableStateOf(emptySet<String>()) }
    var failed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val backstack = LocalBackstackScope.requireNotNull()
    val unfollow: (String) -> Unit = { url ->
      if (url !in updating) {
        updating = updating + url
        failed = null
        scope.launch {
          try {
            database.delete(url)
          } catch (exception: Exception) {
            if (exception is CancellationException) {
              throw exception
            }
            failed = url
          } finally {
            updating = updating - url
          }
        }
      }
    }
    return Model(
      shows = feeds.map { it.asPodcast() },
      episodeCounts = feeds.associate { it.feedUrl to it.episodes.size },
      loading = saved == null,
      failed = saved?.isFailure == true,
      detailOpen = detailOpen && selected != null,
      details =
        selected?.let { feed ->
          ShowDetailsModel(
            podcast = feed.asPodcast(),
            episodes =
              ShowEpisodes(
                episodes =
                  feed.episodes.map { episode ->
                    val details =
                      EpisodeDetails(
                        feedUrl = feed.feedUrl,
                        podcastTitle = feed.title,
                        artworkUrl = episode.artworkUrl ?: feed.artworkUrl,
                        episode = episode,
                      )
                    ShowEpisodes.Episode(
                      details = details,
                      onClick = { backstack.push(episodeDetailsFactory.create(details)) },
                    )
                  },
                loading = false,
                failed = false,
                onRetry = {},
              ),
            descriptionExpanded = descriptionExpanded,
            followed = true,
            followEnabled = feed.feedUrl !in updating,
            followFailed = failed == feed.feedUrl,
            onToggleFollow = { unfollow(feed.feedUrl) },
            onToggleDescription = { descriptionExpanded = !descriptionExpanded },
          )
        },
      updating = updating,
      unfollowFailed = failed,
      onUnfollow = unfollow,
      onRetry = { retry++ },
      onSelectShow = { podcast ->
        selectedUrl = podcast.id
        detailOpen = true
      },
      onBack = { detailOpen = false },
    )
  }

  @Composable
  private fun savedShows(retry: Int): Result<List<PodcastFeed>>? =
    key(retry) {
      val state by
        produceState<Result<List<PodcastFeed>>?>(null) {
          try {
            database.podcasts.collect { value = Result.success(it) }
          } catch (exception: Exception) {
            if (exception is CancellationException) {
              throw exception
            }
            value = Result.failure(exception)
          }
        }
      state
    }

  private fun PodcastFeed.asPodcast() =
    DiscoveredPodcast(
      id = feedUrl,
      title = title,
      author = author,
      feedUrl = feedUrl,
      artworkUrl = artworkUrl,
      catalogUrl = link,
      description = HtmlDescription.plainText(description),
    )

  data class Model(
    val shows: List<DiscoveredPodcast>,
    val episodeCounts: Map<String, Int>,
    val loading: Boolean,
    val failed: Boolean,
    val detailOpen: Boolean,
    val details: ShowDetailsModel?,
    val updating: Set<String>,
    val unfollowFailed: String?,
    val onUnfollow: (String) -> Unit,
    val onRetry: () -> Unit,
    val onSelectShow: (DiscoveredPodcast) -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
