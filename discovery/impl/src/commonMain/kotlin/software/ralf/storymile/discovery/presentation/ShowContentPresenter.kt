package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.database.PodcastDatabase
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState

class ShowContentPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val database: PodcastDatabase,
  private val initialPodcast: DiscoveredPodcast,
  private val podcasts: List<DiscoveredPodcast>,
  private val source: ShowPresenter.Source,
  private val onBack: () -> Unit,
) : ComposePresenter<Unit, ShowContentPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    var podcast by remember { mutableStateOf(initialPodcast) }
    var descriptionExpanded by remember(podcast.id) { mutableStateOf(false) }
    val lookup =
      key(podcast.id) {
        val state by remember { catalog.lookup(podcast.id) }.collectAsState()
        state
      }
    val episodesPresenter = remember {
      EpisodesPresenter(catalog, feeds, episodeDetailsFactory, replaceOpenEpisode = true)
    }
    val selected =
      (lookup as? State.Success)?.podcasts?.firstOrNull { it.id == podcast.id } ?: podcast
    val feedUrl = (selected.feedUrl ?: podcast.feedUrl)?.trim()?.takeIf { it.isNotEmpty() }
    val feedState =
      key(feedUrl) {
        feedUrl?.let { url ->
          val state by remember { feeds.feed(url) }.collectAsState()
          state
        }
      }
    val feed = (feedState as? FeedState.Success)?.feed
    var databaseRetry by remember { mutableIntStateOf(0) }
    val saved = savedFeedUrls(databaseRetry)
    var updating by remember(podcast.id, feedUrl) { mutableStateOf(false) }
    var updateFailed by remember(podcast.id, feedUrl) { mutableStateOf(false) }
    val followed = saved?.getOrNull()?.contains(feedUrl) == true
    val followEnabled = !updating && saved != null && (followed || feed != null || saved.isFailure)
    val coroutineScope = rememberCoroutineScope()
    return Model(
      podcast = selected,
      podcasts = podcasts,
      source = source,
      episodes = episodesPresenter.present(listOf(podcast)),
      descriptionExpanded = descriptionExpanded,
      followed = followed,
      followEnabled = followEnabled,
      followFailed = updateFailed || saved?.isFailure == true,
      onToggleFollow = {
        if (saved?.isFailure == true) {
          databaseRetry++
        } else if (followEnabled && !updating) {
          updating = true
          updateFailed = false
          coroutineScope.launch {
            try {
              setFollowed(feedUrl = requireNotNull(feedUrl), feed = feed, followed = !followed)
            } catch (exception: Exception) {
              if (exception is CancellationException) {
                throw exception
              }
              updateFailed = true
            } finally {
              updating = false
            }
          }
        }
      },
      onToggleDescription = { descriptionExpanded = !descriptionExpanded },
      onPodcastSelected = { podcast = it },
      onBack = onBack,
    )
  }

  private suspend fun setFollowed(feedUrl: String, feed: PodcastFeed?, followed: Boolean) {
    if (followed) {
      database.insert(requireNotNull(feed).copy(feedUrl = feedUrl))
    } else {
      database.delete(feedUrl)
    }
  }

  @Composable
  private fun savedFeedUrls(retry: Int): Result<Set<String>>? =
    key(retry) {
      val state by
        produceState<Result<Set<String>>?>(null) {
          try {
            database.podcasts.collect { podcasts ->
              value = Result.success(podcasts.map { it.feedUrl }.toSet())
            }
          } catch (exception: Exception) {
            if (exception is CancellationException) {
              throw exception
            }
            value = Result.failure(exception)
          }
        }
      state
    }

  data class Model(
    val podcast: DiscoveredPodcast,
    val podcasts: List<DiscoveredPodcast>,
    val source: ShowPresenter.Source,
    val episodes: EpisodesPresenter.Model,
    val descriptionExpanded: Boolean,
    val followed: Boolean,
    val followEnabled: Boolean,
    val followFailed: Boolean,
    val onToggleFollow: () -> Unit,
    val onToggleDescription: () -> Unit,
    val onPodcastSelected: (DiscoveredPodcast) -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
