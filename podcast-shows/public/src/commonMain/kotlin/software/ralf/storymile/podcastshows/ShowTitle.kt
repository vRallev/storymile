package software.ralf.storymile.podcastshows

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.discovery.DiscoveryTopic
import software.ralf.storymile.podcast.shows.generated.resources.Res
import software.ralf.storymile.podcast.shows.generated.resources.discover_podcasts
import software.ralf.storymile.podcast.shows.generated.resources.library
import software.ralf.storymile.podcast.shows.generated.resources.search_results
import software.ralf.storymile.podcast.shows.generated.resources.topic_culture
import software.ralf.storymile.podcast.shows.generated.resources.topic_nature
import software.ralf.storymile.podcast.shows.generated.resources.topic_science
import software.ralf.storymile.podcast.shows.generated.resources.topic_technology

/** Returns the localized title shared with the screen that opened the show. */
@Composable
fun ShowSource.title(): String =
  stringResource(
    when (this) {
      ShowSource.Library -> Res.string.library
      ShowSource.Browse -> Res.string.discover_podcasts
      ShowSource.Search -> Res.string.search_results
      is ShowSource.Topic ->
        when (topic) {
          DiscoveryTopic.NATURE -> Res.string.topic_nature
          DiscoveryTopic.SCIENCE -> Res.string.topic_science
          DiscoveryTopic.CULTURE -> Res.string.topic_culture
          DiscoveryTopic.TECHNOLOGY -> Res.string.topic_technology
        }
    },
  )
