package software.ralf.storymile.librarysaved

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.LibraryEpisodeRow
import software.ralf.storymile.design.ListDivider
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeSharedElement
import software.ralf.storymile.episodedetails.episodeSharedElement
import software.ralf.storymile.library.saved.impl.generated.resources.Res
import software.ralf.storymile.library.saved.impl.generated.resources.library_saved_remove

@Composable
internal fun LibrarySavedList(
  episodes: List<EpisodeDetails>,
  selectedEpisodeKey: String?,
  onSelect: (EpisodeDetails) -> Unit,
  onRemove: (EpisodeDetails) -> Unit,
  state: LazyListState,
  modifier: Modifier,
) {
  BoxWithConstraints(modifier) {
    val compact = maxWidth / LocalDensity.current.fontScale < 400.dp
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("library-saved-list"),
      state = state,
      contentPadding = PaddingValues(vertical = 16.dp),
    ) {
      itemsIndexed(episodes, key = { _, details -> details.key }) { index, details ->
        Column(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = tween(durationMillis = 150))) {
          if (index > 0) {
            ListDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
          }
          SavedEpisodeRow(
            details = details,
            index = index,
            compact = compact,
            selected = selectedEpisodeKey?.let { it == details.key },
            onSelect = { onSelect(details) },
            onRemove = { onRemove(details) },
            modifier = Modifier.testTag("library-saved-episode-$index"),
          )
        }
      }
    }
  }
}

@Composable
private fun SavedEpisodeRow(
  details: EpisodeDetails,
  index: Int,
  compact: Boolean,
  selected: Boolean?,
  onSelect: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier,
) {
  LibraryEpisodeRow(
    title = details.episode.title,
    podcastTitle = details.podcastTitle,
    duration = details.duration,
    compact = compact,
    selected = selected,
    onClick = onSelect,
    modifier = modifier,
    titleModifier =
      Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Title)
        .testTag("library-saved-title-$index"),
    subtitleModifier =
      Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Subtitle)
        .testTag("library-saved-subtitle-$index"),
    durationModifier = {
      Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Duration)
        .testTag("library-saved-duration-$index")
    },
    artwork = { size ->
      PodcastArtwork(
        artworkUrl = details.artworkUrl,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Artwork)
            .testTag("library-saved-artwork-$index")
            .then(size),
      )
    },
  ) { metadataColor ->
    IconButton(onClick = onRemove, modifier = Modifier.testTag("library-saved-remove-$index")) {
      Icon(
        imageVector = Icons.Filled.Close,
        contentDescription = stringResource(Res.string.library_saved_remove, details.episode.title),
        tint = metadataColor,
      )
    }
  }
}
