package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.rss.PodcastFeedRepository
import software.ralf.storymile.rss.PodcastFeedRepository.State

class EpisodesPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val repository: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val episodesPerPodcast: Int = Int.MAX_VALUE,
) : ComposePresenter<List<DiscoveredPodcast>, EpisodesPresenter.Model> {
  @Composable
  override fun present(input: List<DiscoveredPodcast>): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val sources =
      input
        .distinctBy { it.id }
        .map { podcast ->
          key(podcast.id) {
            if (!podcast.feedUrl.isNullOrBlank()) {
              Source(podcast.feedUrl, null, null)
            } else {
              val lookup by remember(podcast.id) { catalog.lookup(podcast.id) }.collectAsState()
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
    return Model(
      episodes =
        states.flatMap { (url, state) ->
          if (state is State.Success) {
            state.feed.episodes.take(episodesPerPodcast).map { episode ->
              val details =
                EpisodeDetails(
                  feedUrl = url,
                  podcastTitle = state.feed.title,
                  artworkUrl = episode.artworkUrl ?: state.feed.artworkUrl,
                  episode = episode,
                )
              Episode(
                details = details,
                onClick = { backstack.push(episodeDetailsFactory.create(details)) },
              )
            }
          } else {
            emptyList()
          }
        },
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

  private data class Source(val url: String?, val id: String?, val lookup: CatalogState?)

  data class Episode(val details: EpisodeDetails, val onClick: () -> Unit) {
    val key: String = details.key
  }

  data class Model(
    val episodes: List<Episode>,
    val loading: Boolean,
    val failed: Boolean,
    val onRetry: () -> Unit,
  ) : BaseModel
}
