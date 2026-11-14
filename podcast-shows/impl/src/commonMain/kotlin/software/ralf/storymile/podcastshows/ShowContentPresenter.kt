package software.ralf.storymile.podcastshows

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.backstack
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State as CatalogState
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.PodcastFeedRepository
import software.ralf.storymile.podcast.PodcastFeedRepository.State as FeedState
import software.ralf.storymile.podcast.SavedShowRepository
import software.ralf.storymile.util.HtmlDescription

class ShowContentPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val savedShows: SavedShowRepository,
  private val selection: ShowSelection,
  private val source: ShowSource,
  private val onBack: () -> Unit,
) : ComposePresenter<Unit, ShowContentPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val saved by savedShows.shows.collectAsState()
    val openEpisode: (EpisodeDetails) -> Unit = { episode ->
      val presenter = episodeDetailsFactory.create(episode)
      if (backstack.backstack.size > 1) {
        backstack.replaceTop(presenter)
      } else {
        backstack.push(presenter)
      }
    }
    return when (selection) {
      is ShowSelection.Discovery ->
        DiscoveryShow(selection = selection, saved = saved, openEpisode = openEpisode)
      is ShowSelection.Saved ->
        SavedShow(selection = selection, saved = saved, openEpisode = openEpisode)
    }
  }

  @Composable
  private fun DiscoveryShow(
    selection: ShowSelection.Discovery,
    saved: List<PodcastFeed>?,
    openEpisode: (EpisodeDetails) -> Unit,
  ): Model {
    var podcast by remember { mutableStateOf(selection.initialPodcast) }
    val lookup =
      key(podcast.id) {
        val state by remember { catalog.lookup(podcast.id) }.collectAsState()
        state
      }
    val selected =
      (lookup as? CatalogState.Success)?.podcasts?.firstOrNull { it.id == podcast.id } ?: podcast
    val feedUrl = (selected.feedUrl ?: podcast.feedUrl)?.trim()?.takeIf { it.isNotEmpty() }
    val feedState =
      key(feedUrl) {
        feedUrl?.let { url ->
          val state by remember { feeds.feed(url) }.collectAsState()
          state
        }
      }
    val feed = (feedState as? FeedState.Success)?.feed
    val episodes =
      EpisodesModel(
        episodes =
          feed?.episodeRows(feedUrl = requireNotNull(feedUrl), onSelect = openEpisode).orEmpty(),
        loading =
          if (feedUrl == null) lookup == CatalogState.Loading else feedState == FeedState.Loading,
        failed =
          if (feedUrl == null) lookup == CatalogState.Error else feedState == FeedState.Error,
        onRetry = {
          if (feedState == FeedState.Error) {
            feeds.refresh(requireNotNull(feedUrl))
          } else if (feedUrl == null && lookup == CatalogState.Error) {
            catalog.refresh(podcast.id)
          }
        },
      )
    return showModel(
      podcast =
        ShowPreview(
          id = selected.id,
          title = selected.title,
          author = selected.author,
          artworkUrl = selected.artworkUrl,
          description = selected.description,
        ),
      episodes = episodes,
      followed = saved != null && saved.any { it.feedUrl == feedUrl },
      canToggleFollow = saved != null,
      feedUrl = feedUrl,
      feedToSave = feed,
      pane =
        ShowListPane.Discovery(
          podcasts = selection.podcasts,
          onSelect = { podcast = it },
        ),
    )
  }

  @Composable
  private fun SavedShow(
    selection: ShowSelection.Saved,
    saved: List<PodcastFeed>?,
    openEpisode: (EpisodeDetails) -> Unit,
  ): Model {
    var selected by remember { mutableStateOf(selection.initialShow) }
    val stored = saved?.firstOrNull { it.feedUrl == selected.feedUrl }
    val snapshot = stored ?: selected
    val latest by
      key(snapshot.feedUrl) {
        // Keep the saved snapshot or last successful feed visible during refreshes.
        produceState<PodcastFeed?>(null) {
          feeds.feed(snapshot.feedUrl).collect { state ->
            if (state is FeedState.Success) {
              value = state.feed
            }
          }
        }
      }
    val show =
      latest?.let {
        it.copy(
          feedUrl = snapshot.feedUrl,
          description = it.description ?: snapshot.description,
          author = it.author ?: snapshot.author,
          artworkUrl = it.artworkUrl ?: snapshot.artworkUrl,
        )
      } ?: snapshot
    val shows = remember(saved) { saved.orEmpty().sortedBy { it.title.lowercase() } }
    return showModel(
      podcast =
        ShowPreview(
          id = show.feedUrl,
          title = show.title,
          author = show.author,
          artworkUrl = show.artworkUrl,
          description = remember(show.description) { HtmlDescription.plainText(show.description) },
        ),
      episodes =
        EpisodesModel(
          episodes = show.episodeRows(onSelect = openEpisode),
          loading = false,
          failed = false,
          onRetry = {},
        ),
      followed = stored != null,
      canToggleFollow = saved != null,
      feedUrl = show.feedUrl,
      feedToSave = show,
      pane =
        ShowListPane.Saved(
          shows = shows,
          onSelect = { selected = it },
        ),
    )
  }

  @Composable
  private fun showModel(
    podcast: ShowPreview,
    episodes: EpisodesModel,
    followed: Boolean,
    canToggleFollow: Boolean,
    feedUrl: String?,
    feedToSave: PodcastFeed?,
    pane: ShowListPane,
  ): Model {
    var descriptionExpanded by remember(podcast.id) { mutableStateOf(false) }
    val followEnabled = canToggleFollow && (followed || feedToSave != null)
    return Model(
      podcast = podcast,
      pane = pane,
      source = source,
      episodes = episodes,
      descriptionExpanded = descriptionExpanded,
      followed = followed,
      followEnabled = followEnabled,
      onToggleFollow = {
        if (followEnabled) {
          val url = requireNotNull(feedUrl)
          if (followed) {
            savedShows.remove(url)
          } else {
            savedShows.save(requireNotNull(feedToSave).copy(feedUrl = url))
          }
        }
      },
      onToggleDescription = { descriptionExpanded = !descriptionExpanded },
      onBack = onBack,
    )
  }

  data class Model(
    val podcast: ShowPreview,
    val pane: ShowListPane,
    val source: ShowSource,
    val episodes: EpisodesModel,
    val descriptionExpanded: Boolean,
    val followed: Boolean,
    val followEnabled: Boolean,
    val onToggleFollow: () -> Unit,
    val onToggleDescription: () -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
