package software.ralf.storymile.show

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.PodcastFeedRepository.State
import software.ralf.storymile.show.EpisodesPresenter.Episode
import software.ralf.storymile.show.EpisodesPresenter.Model
import software.ralf.storymile.user.UserScope

internal class EpisodesPresenterImpl(
  private val catalog: PodcastDiscoveryRepository,
  private val repository: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val episodesPerPodcast: Int = Int.MAX_VALUE,
  private val replaceOpenEpisode: Boolean = false,
) : EpisodesPresenter {
  @Composable
  override fun present(input: List<DiscoveredPodcast>): Model {
    val sources =
      input
        .distinctBy { it.id }
        .map { podcast ->
          key(podcast.id) {
            if (!podcast.feedUrl.isNullOrBlank()) {
              Source(podcast.feedUrl, null, null)
            } else {
              val lookup by remember { catalog.lookup(podcast.id) }.collectAsState()
              val url =
                (lookup as? CatalogState.Success)
                  ?.podcasts
                  ?.firstOrNull { it.id == podcast.id }
                  ?.feedUrl
              Source(url, podcast.id, lookup)
            }
          }
        }
    val urls = sources.mapNotNull { it.url?.trim()?.takeIf { url -> url.isNotEmpty() } }.distinct()
    val states = urls.map { url ->
      key(url) {
        val state by remember(url) { repository.feed(url) }.collectAsState()
        url to state
      }
    }
    val feeds = states.mapNotNull { (url, state) ->
      (state as? State.Success)?.feed?.let { url to it }
    }
    return presentFeeds(
      feeds = feeds,
      loading =
        sources.any { it.lookup == CatalogState.Loading } ||
          states.any { it.second == State.Loading },
      failed =
        sources.any { it.lookup == CatalogState.Error } || states.any { it.second == State.Error },
      onRetry = {
        sources
          .filter { it.lookup == CatalogState.Error }
          .forEach { catalog.refresh(requireNotNull(it.id)) }
        states.filter { it.second == State.Error }.forEach { repository.refresh(it.first) }
      },
    )
  }

  @Composable
  fun presentFeed(url: String?, state: State?, savedFeed: PodcastFeed?): Model {
    val feed = (state as? State.Success)?.feed ?: savedFeed
    return presentFeeds(
      feeds = if (url != null && feed != null) listOf(url to feed) else emptyList(),
      loading = savedFeed == null && state == State.Loading,
      failed = savedFeed == null && state == State.Error,
      onRetry = { url?.let { repository.refresh(it) } },
    )
  }

  @Composable
  private fun presentFeeds(
    feeds: List<Pair<String, PodcastFeed>>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
  ): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val episodes = feeds.flatMap { (url, feed) ->
      feed.episodes.take(episodesPerPodcast).map { episode ->
        val details =
          EpisodeDetails(
            feedUrl = url,
            podcastTitle = feed.title,
            artworkUrl = episode.artworkUrl ?: feed.artworkUrl,
            episode = episode,
          )
        Episode(
          details = details,
          onClick = {
            val presenter = episodeDetailsFactory.create(details)
            if (replaceOpenEpisode && backstack.backstack.size > 1) {
              backstack.replaceTop(presenter)
            } else {
              backstack.push(presenter)
            }
          },
        )
      }
    }
    return Model(episodes = episodes, loading = loading, failed = failed, onRetry = onRetry)
  }

  private data class Source(val url: String?, val id: String?, val lookup: CatalogState?)

  @Inject
  @ContributesBinding(UserScope::class)
  class Factory(
    private val catalog: PodcastDiscoveryRepository,
    private val repository: PodcastFeedRepository,
    private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  ) : EpisodesPresenter.Factory {
    override fun create(episodesPerPodcast: Int): EpisodesPresenter =
      EpisodesPresenterImpl(
        catalog = catalog,
        repository = repository,
        episodeDetailsFactory = episodeDetailsFactory,
        episodesPerPodcast = episodesPerPodcast,
      )
  }
}
