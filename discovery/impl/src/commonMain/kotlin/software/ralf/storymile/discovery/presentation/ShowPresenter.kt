package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.backstack.nav3.LocalBackstackScope
import software.ralf.app.platform.presenter.backstack.nav3.requireNotNull
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.rss.PodcastFeedRepository

class ShowPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val initialPodcast: DiscoveredPodcast,
  private val podcasts: List<DiscoveredPodcast>,
  private val source: Source,
) : ComposePresenter<Unit, ShowPresenter.Model> {
  @Composable
  override fun present(input: Unit): Model {
    val backstack = LocalBackstackScope.requireNotNull()
    var podcast by remember { mutableStateOf(initialPodcast) }
    val episodesPresenter = remember { EpisodesPresenter(catalog, feeds) }
    return Model(
      podcast = podcast,
      podcasts = podcasts,
      source = source,
      episodes = episodesPresenter.present(listOf(podcast)),
      onPodcastSelected = { podcast = it },
      onBack = { backstack.pop() },
    )
  }

  sealed interface Source {
    data object Browse : Source

    data object Search : Source

    data class Topic(val topic: DiscoveryTopic) : Source
  }

  data class Model(
    val podcast: DiscoveredPodcast,
    val podcasts: List<DiscoveredPodcast>,
    val source: Source,
    val episodes: EpisodesPresenter.Model,
    val onPodcastSelected: (DiscoveredPodcast) -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
