package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import software.ralf.storymile.http.HttpResponse

class DiscoveryPresenterImpl(
  private val repository: PodcastDiscoveryRepository,
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
    var state by remember(term, retry) { mutableStateOf<CatalogState>(CatalogState.Loading) }

    LaunchedEffect(term, retry) {
      if (term.isNotEmpty() && topic == null && retry == 0) {
        delay(300)
      }
      val response = if (term.isEmpty()) repository.popular() else repository.search(term)
      coroutineContext.ensureActive()
      state =
        when (response) {
          is HttpResponse.Success -> CatalogState.Ready(response.data)
          else -> CatalogState.Error
        }
    }

    return Model(
      topic = topic,
      query = query,
      state = state,
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
      onTopicSelected = { backstack.push(DiscoveryPresenterImpl(repository, topic = it)) },
      onPodcastSelected = { podcast ->
        backstack.push(
          ShowPresenter(
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
      onRetry = { retry++ },
      onBack = { backstack.pop() },
    )
  }

  data class Model(
    val topic: DiscoveryTopic?,
    val query: PresenterTextFieldState,
    val state: CatalogState,
    val focusSearch: Boolean,
    val selectedFilter: DiscoveryTopic.Filter?,
    val onFilterSelected: (DiscoveryTopic.Filter) -> Unit,
    val onTopicSelected: (DiscoveryTopic) -> Unit,
    val onPodcastSelected: (DiscoveredPodcast) -> Unit,
    val onRetry: () -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
