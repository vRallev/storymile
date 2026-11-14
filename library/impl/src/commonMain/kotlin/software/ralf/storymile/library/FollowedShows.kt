package software.ralf.storymile.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_following
import software.ralf.storymile.library.impl.generated.resources.library_show_episode_count
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.show.ShowOrigin
import software.ralf.storymile.show.ShowSharedElement
import software.ralf.storymile.show.showSharedElement
import software.ralf.storymile.templates.LocalSharedTransitionScope
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun FollowedShows(
  shows: List<PodcastFeed>,
  selectedShowUrl: String?,
  onShowSelected: (PodcastFeed) -> Unit,
  state: LazyGridState,
  asGrid: Boolean,
  inPane: Boolean,
  modifier: Modifier,
) {
  Box(modifier, contentAlignment = Alignment.TopCenter) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(if (asGrid) 2 else 1),
      modifier = Modifier.widthIn(max = 760.dp).fillMaxSize().testTag("library-followed-shows"),
      state = state,
      contentPadding = PaddingValues(vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(if (asGrid) 16.dp else 0.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
      item(key = "followed-shows-heading", span = { GridItemSpan(maxLineSpan) }) {
        Text(
          text = ShowOrigin.Library.title(),
          modifier = Modifier.padding(bottom = if (asGrid) 0.dp else 8.dp).semantics { heading() },
          style = AppTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
      itemsIndexed(shows, key = { _, show -> show.feedUrl }) { index, show ->
        val selected = inPane && show.feedUrl == selectedShowUrl
        Column {
          Surface(
            onClick = { onShowSelected(show) },
            modifier =
              Modifier.fillMaxWidth()
                .testTag("library-show-${show.feedUrl}")
                .then(
                  if (inPane) Modifier.semantics { this.selected = selected } else Modifier,
                ),
            color = if (selected) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = RoundedCornerShape(12.dp),
          ) {
            if (asGrid) {
              Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                ShowArtwork(
                  show = show,
                  inPane = false,
                  selected = false,
                  modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth().aspectRatio(1f),
                )
                ShowTitle(show = show, inPane = false, textAlign = TextAlign.Center)
                FollowingBadge()
              }
            } else {
              ShowRow(show = show, inPane = inPane, selected = selected)
            }
          }
          if (!asGrid && index < shows.lastIndex) {
            HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
          }
        }
      }
    }
  }
}

@Composable
private fun ShowRow(show: PodcastFeed, inPane: Boolean, selected: Boolean) {
  Row(
    modifier = Modifier.padding(vertical = 8.dp).padding(horizontal = if (inPane) 8.dp else 0.dp),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    ShowArtwork(
      show = show,
      inPane = inPane,
      selected = selected,
      modifier = Modifier.size(if (inPane) 64.dp else 80.dp),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      ShowTitle(show = show, inPane = inPane)
      val count = show.episodes.size
      Text(
        text =
          if (inPane) {
            pluralStringResource(Res.plurals.library_show_episode_count, count, count)
          } else {
            stringResource(Res.string.library_following)
          },
        color = AppTheme.colorScheme.onSurfaceVariant,
        style = AppTheme.typography.bodyMedium,
      )
    }
    if (inPane) {
      FollowingBadge()
    } else {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = AppTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun ShowArtwork(show: PodcastFeed, inPane: Boolean, selected: Boolean, modifier: Modifier) {
  Box(modifier) {
    val sharedScope = LocalSharedTransitionScope.current
    if (selected && sharedScope != null && !sharedScope.isTransitionActive) {
      // The selected row keeps its cover after the detail has finished taking it over.
      PodcastArtwork(artworkUrl = show.artworkUrl, modifier = Modifier.matchParentSize())
    }
    PodcastArtwork(
      artworkUrl = show.artworkUrl,
      modifier =
        Modifier.showSharedElement(
            podcastId = show.feedUrl,
            source = ShowOrigin.Library,
            element = ShowSharedElement.Artwork,
            visibleInPane = if (inPane) !selected else null,
          )
          .testTag("library-show-artwork-${show.feedUrl}")
          .matchParentSize(),
    )
  }
}

@Composable
private fun ShowTitle(show: PodcastFeed, inPane: Boolean, textAlign: TextAlign? = null) {
  Text(
    text = show.title,
    modifier =
      Modifier.showSharedElement(
        podcastId = show.feedUrl,
        source = if (inPane) null else ShowOrigin.Library,
        element = ShowSharedElement.Title,
      ),
    style = AppTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
  )
}

@Composable
private fun FollowingBadge() {
  Surface(
    color = AppTheme.colorScheme.primaryContainer,
    contentColor = AppTheme.colorScheme.onPrimaryContainer,
    shape = RoundedCornerShape(50),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
      )
      Text(
        text = stringResource(Res.string.library_following),
        style = AppTheme.typography.labelMedium,
      )
    }
  }
}
