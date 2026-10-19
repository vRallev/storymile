package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.text.PresenterTextFieldState
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.DiscoveryPresenter
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.rss.PodcastFeedRepository

class DiscoveryPresenterImpl(
  private val repository: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
  private val topic: DiscoveryTopic? = null,
  private val focusSearch: Boolean = false,
) : DiscoveryPresenter<DiscoveryPresenterImpl.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    val query = remember { PresenterTextFieldState(topic?.searchTerm.orEmpty()) }
    var selectedFilter by remember { mutableStateOf(topic?.filters?.firstOrNull()) }
    val term = query.value.trim()
    var retry by remember(term) { mutableIntStateOf(0) }
    val state = catalogState(term, retry)
    val episodesPresenter = remember {
      EpisodesPresenter(repository, feeds, episodeDetailsFactory, episodesPerPodcast = 1)
    }
    val episodePodcasts =
      if (term.isEmpty() || topic != null) {
        (state as? CatalogState.Ready)
          ?.podcasts
          .orEmpty()
          .distinctBy { it.feedUrl?.trim()?.takeIf { url -> url.isNotEmpty() } ?: it.id }
          .take(2)
      } else {
        emptyList()
      }

    return Model(
      topic = topic,
      query = query,
      state = state,
      episodes = episodesPresenter.present(episodePodcasts),
      focusSearch = focusSearch,
      selectedFilter = selectedFilter,
      onFilterSelected = { filter ->
        if (topic != null && filter in topic.filters) {
          selectedFilter = filter
          query.replaceText(
            if (filter.searchTerm.isEmpty()) {
              topic.searchTerm
            } else {
              "${topic.searchTerm} ${filter.searchTerm}"
            },
          )
        }
      },
      onTopicSelected = {
        backstack.push(DiscoveryPresenterImpl(repository, feeds, episodeDetailsFactory, topic = it))
      },
      onPodcastSelected = { podcast ->
        backstack.push(
          ShowPresenter(
            catalog = repository,
            feeds = feeds,
            episodeDetailsFactory = episodeDetailsFactory,
            initialPodcast = podcast,
            podcasts = (state as? CatalogState.Ready)?.podcasts.orEmpty(),
            source =
              when {
                topic != null -> ShowPresenter.Source.Topic(topic)
                term.isEmpty() -> ShowPresenter.Source.Browse
                else -> ShowPresenter.Source.Search
              },
          ),
        )
      },
      onRetry = {
        if (term.isEmpty()) {
          repository.refresh()
        } else {
          retry++
        }
      },
      onBack = { backstack.pop() },
    )
  }

  @Composable
  private fun catalogState(term: String, retry: Int): CatalogState {
    return if (term.isEmpty()) {
      val popular by repository.popular.collectAsState()
      when (val current = popular) {
        State.Loading -> CatalogState.Loading
        State.Error -> CatalogState.Error
        is State.Success -> CatalogState.Ready(current.podcasts)
      }
    } else {
      var searchState by
        remember(term, retry) { mutableStateOf<CatalogState>(CatalogState.Loading) }
      LaunchedEffect(term, retry) {
        if (topic == null && retry == 0) {
          delay(300)
        }
        val response = repository.search(term)
        coroutineContext.ensureActive()
        searchState =
          when (response) {
            is HttpResponse.Success -> CatalogState.Ready(response.data)
            else -> CatalogState.Error
          }
      }
      searchState
    }
  }

  data class Model(
    val topic: DiscoveryTopic?,
    val query: PresenterTextFieldState,
    val state: CatalogState,
    val episodes: EpisodesPresenter.Model,
    val focusSearch: Boolean,
    val selectedFilter: DiscoveryTopic.Filter?,
    val onFilterSelected: (DiscoveryTopic.Filter) -> Unit,
    val onTopicSelected: (DiscoveryTopic) -> Unit,
    val onPodcastSelected: (DiscoveredPodcast) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
