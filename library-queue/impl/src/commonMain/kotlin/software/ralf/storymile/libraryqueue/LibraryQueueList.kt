package software.ralf.storymile.libraryqueue

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.DensityMedium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.ListDivider
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.episodedetails.EpisodeSharedElement
import software.ralf.storymile.episodedetails.episodeSharedElement
import software.ralf.storymile.library.queue.impl.generated.resources.Res
import software.ralf.storymile.library.queue.impl.generated.resources.library_queue_remove
import software.ralf.storymile.library.queue.impl.generated.resources.library_queue_reorder
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun LibraryQueueList(
  episodes: List<EpisodeDetails>,
  selectedEpisodeKey: String?,
  onSelect: (EpisodeDetails) -> Unit,
  onReorder: (EpisodeDetails) -> Unit,
  onRemove: (EpisodeDetails) -> Unit,
  state: LazyListState,
  modifier: Modifier = Modifier,
) {
  BoxWithConstraints(modifier) {
    val compact = maxWidth / LocalDensity.current.fontScale < 400.dp
    LazyColumn(
      modifier = Modifier.fillMaxWidth().testTag("library-queue-list"),
      state = state,
      contentPadding = PaddingValues(vertical = 16.dp),
    ) {
      itemsIndexed(episodes, key = { _, details -> details.key }) { index, details ->
        val isSelected = details.key == selectedEpisodeKey
        val rowModifier = Modifier.testTag("library-queue-episode-$index")
        Column {
          if (index > 0) {
            ListDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
          }
          QueueEpisodeRow(
            details = details,
            index = index,
            compact = compact,
            isSelected = isSelected,
            onSelect = { onSelect(details) },
            onReorder = { onReorder(details) },
            onRemove = { onRemove(details) },
            modifier =
              if (selectedEpisodeKey != null) {
                rowModifier.semantics { selected = isSelected }
              } else {
                rowModifier
              },
          )
        }
      }
    }
  }
}

@Composable
private fun QueueEpisodeRow(
  details: EpisodeDetails,
  index: Int,
  compact: Boolean,
  isSelected: Boolean,
  onSelect: () -> Unit,
  onReorder: () -> Unit,
  onRemove: () -> Unit,
  modifier: Modifier,
) {
  val currentOnReorder by rememberUpdatedState(onReorder)
  val titleStyle = if (compact) AppTheme.typography.titleSmall else AppTheme.typography.titleMedium
  val metadataStyle = if (compact) AppTheme.typography.bodySmall else AppTheme.typography.bodyMedium
  val metadataColor =
    if (isSelected) {
      AppTheme.colorScheme.onSecondaryContainer
    } else {
      AppTheme.colorScheme.onSurfaceVariant
    }
  Surface(
    onClick = onSelect,
    modifier = modifier.fillMaxWidth(),
    shape = AppTheme.shapes.medium,
    color = if (isSelected) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
  ) {
    Row(
      modifier = Modifier.padding(8.dp),
      horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PodcastArtwork(
        artworkUrl = details.artworkUrl,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Artwork)
            .testTag("library-queue-artwork-$index")
            .size(if (compact) 64.dp else 88.dp),
      )
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Text(
          text = details.episode.title,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Title)
              .testTag("library-queue-title-$index"),
          style = titleStyle.copy(textMotion = TextMotion.Animated),
          fontWeight = FontWeight.SemiBold,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = details.podcastTitle,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Subtitle)
              .testTag("library-queue-subtitle-$index"),
          style = metadataStyle.copy(textMotion = TextMotion.Animated),
          color = metadataColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        details.duration?.let { duration ->
          Text(
            text = duration,
            modifier =
              Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Duration)
                .testTag("library-queue-duration-$index"),
            style = metadataStyle.copy(textMotion = TextMotion.Animated),
            color = metadataColor,
          )
        }
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onReorder,
          modifier =
            Modifier.testTag("library-queue-reorder-$index").pointerInput(details.key) {
              detectVerticalDragGestures(
                onDragEnd = { currentOnReorder() },
                onVerticalDrag = { _, _ -> },
              )
            },
        ) {
          Icon(
            imageVector = Icons.Rounded.DensityMedium,
            contentDescription =
              stringResource(Res.string.library_queue_reorder, details.episode.title),
            tint = metadataColor,
          )
        }
        if (!compact) {
          VerticalDivider(
            modifier = Modifier.height(32.dp),
            color = AppTheme.colorScheme.outlineVariant,
          )
        }
        IconButton(
          onClick = onRemove,
          modifier = Modifier.testTag("library-queue-remove-$index"),
        ) {
          Icon(
            imageVector = Icons.Filled.Close,
            contentDescription =
              stringResource(Res.string.library_queue_remove, details.episode.title),
            tint = metadataColor,
          )
        }
      }
    }
  }
}
