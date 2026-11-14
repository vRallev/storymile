package software.ralf.storymile.podcastshows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.ListDivider
import software.ralf.storymile.design.PodcastRow
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.podcast.shows.generated.resources.Res
import software.ralf.storymile.podcast.shows.generated.resources.followed_shows
import software.ralf.storymile.podcastshows.PodcastSharedElement.Companion.podcastSharedElement

/**
 * Lists followed shows in the supplied order. Use [selectedFeedUrl] in a list-detail layout and
 * leave it null when the rows act as navigation to show details.
 */
@Composable
fun FollowedShows(
  shows: List<PodcastFeed>,
  onShowSelected: (PodcastFeed) -> Unit,
  modifier: Modifier = Modifier,
  state: LazyListState = rememberLazyListState(),
  selectedFeedUrl: String? = null,
) {
  val source = if (selectedFeedUrl == null) ShowSource.Library else null
  LazyColumn(
    modifier = modifier,
    state = state,
    contentPadding = PaddingValues(vertical = 16.dp),
  ) {
    item {
      EpisodesHeading(
        title = stringResource(Res.string.followed_shows),
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
      )
    }
    itemsIndexed(shows, key = { _, show -> show.feedUrl }) { index, show ->
      val id = show.feedUrl
      val isSelected = id == selectedFeedUrl
      val rowModifier = Modifier.testTag("library-show-$id")
      Column {
        if (index > 0) {
          ListDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
        }
        PodcastRow(
          title = show.title,
          author = show.author,
          modifier =
            if (selectedFeedUrl != null) {
              rowModifier.semantics { selected = isSelected }
            } else {
              rowModifier
            },
          onClick = { onShowSelected(show) },
          selected = isSelected,
          titleModifier =
            Modifier.podcastSharedElement(
              podcastId = id,
              source = source,
              element = PodcastSharedElement.Title,
            ),
          authorModifier =
            Modifier.podcastSharedElement(
              podcastId = id,
              source = source,
              element = PodcastSharedElement.Author,
            ),
        ) { artworkModifier ->
          PodcastArtwork(
            artworkUrl = show.artworkUrl,
            modifier =
              Modifier.podcastSharedElement(
                  podcastId = id,
                  source = source,
                  element = PodcastSharedElement.Artwork,
                )
                .then(artworkModifier),
            artworkId = id,
          )
        }
      }
    }
  }
}
