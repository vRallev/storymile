package software.ralf.storymile.discovery.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.PodcastDiscoveryRepository
import software.ralf.storymile.discovery.PodcastDiscoveryRepository.State
import software.ralf.storymile.episodedetails.EpisodeDetailsPresenter
import software.ralf.storymile.podcast.PodcastFeedRepository

class ShowContentPresenter(
  private val catalog: PodcastDiscoveryRepository,
  private val feeds: PodcastFeedRepository,
  private val episodeDetailsFactory: EpisodeDetailsPresenter.Factory,
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
    return Model(
      podcast =
        (lookup as? State.Success)?.podcasts?.firstOrNull { it.id == podcast.id } ?: podcast,
      podcasts = podcasts,
      source = source,
      episodes = episodesPresenter.present(listOf(podcast)),
      descriptionExpanded = descriptionExpanded,
      onToggleDescription = { descriptionExpanded = !descriptionExpanded },
      onPodcastSelected = { podcast = it },
      onBack = onBack,
    )
  }

  data class Model(
    val podcast: DiscoveredPodcast,
    val podcasts: List<DiscoveredPodcast>,
    val source: ShowPresenter.Source,
    val episodes: EpisodesPresenter.Model,
    val descriptionExpanded: Boolean,
    val onToggleDescription: () -> Unit,
    val onPodcastSelected: (DiscoveredPodcast) -> Unit,
    val onBack: () -> Unit,
  ) : BaseModel
}
