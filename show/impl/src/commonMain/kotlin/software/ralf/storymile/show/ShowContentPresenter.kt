package software.ralf.storymile.show

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
  private val source: ShowOrigin,
  private val onBack: () -> Unit,
  private val savedFeed: PodcastFeed? = null,
) : ComposePresenter<Unit, ShowContentPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    var podcast by remember { mutableStateOf(initialPodcast) }
    var descriptionExpanded by remember(podcast.id) { mutableStateOf(source == ShowOrigin.Library) }
    val lookup = lookup(podcast)
    val episodesPresenter = remember {
      EpisodesPresenterImpl(
        catalog = catalog,
        repository = feeds,
        episodeDetailsFactory = episodeDetailsFactory,
        replaceOpenEpisode = true,
      )
    }
    val selected =
      (lookup as? State.Success)?.podcasts?.firstOrNull { it.id == podcast.id } ?: podcast
    val feedUrl = (selected.feedUrl ?: podcast.feedUrl)?.trim()?.takeIf { it.isNotEmpty() }
    val feedState = feedState(feedUrl)
    val feed = (feedState as? FeedState.Success)?.feed ?: savedFeed
    var databaseRetry by remember { mutableIntStateOf(0) }
    val follow = followState(podcast.id, feedUrl, feed, databaseRetry) { databaseRetry++ }
    val episodes =
      if (feedUrl == null && savedFeed == null) {
        episodesPresenter.present(listOf(podcast))
      } else {
        episodesPresenter.presentFeed(url = feedUrl, state = feedState, savedFeed = savedFeed)
      }
    return Model(
      podcast = selected,
      podcasts = podcasts,
      source = source,
      episodes = episodes,
      descriptionExpanded = descriptionExpanded,
      followed = follow.followed,
      followEnabled = follow.enabled,
      followFailed = follow.failed,
      onToggleFollow = follow.onToggle,
      onToggleDescription = { descriptionExpanded = !descriptionExpanded },
      onPodcastSelected = { podcast = it },
      onBack = onBack,
    )
  }

  @Composable
  private fun lookup(podcast: DiscoveredPodcast): State? {
    if (savedFeed != null) {
      return null
    }
    return key(podcast.id) {
      val state by remember { catalog.lookup(podcast.id) }.collectAsState()
      state
    }
  }

  @Composable
  private fun feedState(feedUrl: String?): FeedState? =
    key(feedUrl) {
      feedUrl?.let { url ->
        val state by remember { feeds.feed(url) }.collectAsState()
        state
      }
    }

  @Composable
  private fun followState(
    podcastId: String,
    feedUrl: String?,
    feed: PodcastFeed?,
    databaseRetry: Int,
    onRetry: () -> Unit,
  ): FollowState {
    val saved = savedFeedUrls(databaseRetry)
    var updating by remember(podcastId, feedUrl) { mutableStateOf(false) }
    var updateFailed by remember(podcastId, feedUrl) { mutableStateOf(false) }
    val followed = saved?.getOrNull()?.contains(feedUrl) == true
    val followEnabled = !updating && saved != null && (followed || feed != null || saved.isFailure)
    val coroutineScope = rememberCoroutineScope()
    return FollowState(
      followed = followed,
      enabled = followEnabled,
      failed = updateFailed || saved?.isFailure == true,
      onToggle = {
        if (saved?.isFailure == true) {
          onRetry()
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

  private data class FollowState(
    val followed: Boolean,
    val enabled: Boolean,
    val failed: Boolean,
    val onToggle: () -> Unit,
  )

  data class Model(
    val podcast: DiscoveredPodcast,
    val podcasts: List<DiscoveredPodcast>,
    val source: ShowOrigin,
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
