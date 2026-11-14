package software.ralf.storymile.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.library.FollowedShowsPresenter.Model
import software.ralf.storymile.library.FollowedShowsPresenter.State
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_episode_count
import software.ralf.storymile.library.impl.generated.resources.library_followed_shows
import software.ralf.storymile.library.impl.generated.resources.library_following
import software.ralf.storymile.library.impl.generated.resources.library_show_actions
import software.ralf.storymile.library.impl.generated.resources.library_shows_loading
import software.ralf.storymile.library.impl.generated.resources.library_shows_read_error
import software.ralf.storymile.library.impl.generated.resources.library_shows_retry
import software.ralf.storymile.library.impl.generated.resources.library_unfollow
import software.ralf.storymile.library.impl.generated.resources.library_unfollow_error
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.shows.component.ShowRow
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.util.HtmlDescription

@Composable
internal fun FollowedShowsPage(
  model: Model,
  grid: Boolean,
  split: Boolean,
  gridState: LazyGridState,
  emptyPage: @Composable () -> Unit,
) {
  when (val state = model.state) {
    State.Loading -> ShowsStatus(loading = true)
    State.ReadFailed -> ShowsStatus(loading = false, onRetry = model.onRetry)
    is State.Loaded -> {
      val selected = model.selectedShow
      if (selected == null) {
        emptyPage()
      } else {
        ShowList(
          shows = state.shows,
          model = model,
          selected = selected.takeIf { split },
          grid = grid,
          gridState = gridState,
        )
      }
    }
  }
}

@Composable
private fun ShowList(
  shows: List<PodcastFeed>,
  model: Model,
  selected: PodcastFeed?,
  grid: Boolean,
  gridState: LazyGridState,
) {
  BoxWithConstraints(Modifier.fillMaxSize().testTag("library-page-shows")) {
    val columns =
      if (grid) GridCells.Adaptive(208.dp * LocalDensity.current.fontScale) else GridCells.Fixed(1)
    val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
    val dividerSpacing = 4.dp
    val showManagement = selected != null && maxWidth / LocalDensity.current.fontScale >= 410.dp
    LazyVerticalGrid(
      columns = columns,
      modifier =
        Modifier.fillMaxSize().testTag(if (grid) "library-shows-grid" else "library-shows-list"),
      state = gridState,
      contentPadding = PaddingValues(horizontal = sidePadding, vertical = 16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalArrangement = Arrangement.spacedBy(if (grid) 16.dp else dividerSpacing),
    ) {
      item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
        Text(
          text = stringResource(Res.string.library_followed_shows),
          modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
          style = AppTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
      itemsIndexed(shows, key = { _, show -> "show-${show.feedUrl}" }) { index, show ->
        val onClick = { model.onSelectShow(show.feedUrl) }
        if (grid) {
          ShowTile(show = show, onClick = onClick)
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(dividerSpacing)) {
            FollowedShowRow(
              show = show,
              selected = selected?.feedUrl == show.feedUrl,
              onClick = onClick,
              split = selected != null,
              showManagement = showManagement,
              model = model,
            )
            if (index < shows.lastIndex) {
              HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
          }
        }
      }
    }
  }
}

@Composable
private fun FollowedShowRow(
  show: PodcastFeed,
  selected: Boolean,
  onClick: () -> Unit,
  split: Boolean,
  showManagement: Boolean,
  model: Model,
) {
  val subtitle =
    if (split && showManagement) {
      remember(show.description, show.author) {
        HtmlDescription.plainText(show.description)?.takeIf { it.isNotBlank() } ?: show.author
      }
    } else {
      stringResource(Res.string.library_following)
    }
  val supporting: (@Composable () -> Unit)? =
    if (split && showManagement) {
      { EpisodeCount(show.episodes.size) }
    } else {
      null
    }
  val actions: (@Composable () -> Unit)? =
    if (showManagement) {
      {
        Row(verticalAlignment = Alignment.CenterVertically) {
          ShowFollowingLabel(showCheck = true)
          ShowActions(
            show = show,
            canUnfollow = model.canUnfollow,
            onUnfollow = model.onUnfollow,
            modifier = Modifier.testTag("library-show-row-actions-${show.feedUrl}"),
          )
        }
      }
    } else {
      null
    }
  ShowRow(
    title = show.title,
    subtitle = subtitle,
    onClick = onClick,
    artwork = { modifier ->
      PodcastArtwork(
        artworkUrl = show.artworkUrl,
        artworkId = show.feedUrl,
        modifier = modifier,
      )
    },
    modifier = Modifier.testTag("library-show-${show.feedUrl}"),
    selected = selected,
    supportingContent = supporting,
    trailingContent = actions,
  )
}

@Composable
private fun ShowTile(show: PodcastFeed, onClick: () -> Unit) {
  Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
    Surface(
      onClick = onClick,
      modifier = Modifier.widthIn(max = 240.dp).testTag("library-show-${show.feedUrl}"),
      shape = RoundedCornerShape(16.dp),
      color = Color.Transparent,
    ) {
      Column(
        modifier = Modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PodcastArtwork(
          artworkUrl = show.artworkUrl,
          artworkId = show.feedUrl,
          modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
          text = show.title,
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center,
        )
        ShowFollowingLabel()
      }
    }
  }
}

@Composable
private fun ShowsStatus(loading: Boolean, onRetry: () -> Unit = {}) {
  Column(
    modifier = Modifier.fillMaxSize().padding(16.dp).testTag("library-page-shows"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
  ) {
    if (loading) {
      CircularProgressIndicator(Modifier.size(28.dp))
      Text(stringResource(Res.string.library_shows_loading))
    } else {
      Text(stringResource(Res.string.library_shows_read_error))
      TextButton(onClick = onRetry) { Text(stringResource(Res.string.library_shows_retry)) }
    }
  }
}

@Composable
internal fun ShowFollowingLabel(showCheck: Boolean = false) {
  Surface(shape = RoundedCornerShape(50), color = AppTheme.colorScheme.secondaryContainer) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (showCheck) {
        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
      }
      Text(
        text = stringResource(Res.string.library_following),
        style = AppTheme.typography.labelLarge,
      )
    }
  }
}

@Composable
internal fun ShowActions(
  show: PodcastFeed,
  canUnfollow: Boolean,
  onUnfollow: (String) -> Unit,
  modifier: Modifier,
) {
  var expanded by remember(show.feedUrl) { mutableStateOf(false) }
  Box {
    IconButton(onClick = { expanded = true }, modifier = modifier, enabled = canUnfollow) {
      Icon(Icons.Default.MoreVert, stringResource(Res.string.library_show_actions, show.title))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(Res.string.library_unfollow)) },
        onClick = {
          expanded = false
          onUnfollow(show.feedUrl)
        },
      )
    }
  }
}

@Composable
internal fun EpisodeCount(count: Int) {
  Text(
    text = pluralStringResource(Res.plurals.library_episode_count, count, count),
    style = AppTheme.typography.bodyMedium,
    color = AppTheme.colorScheme.onSurfaceVariant,
  )
}

@Composable
internal fun UnfollowError() {
  Text(
    text = stringResource(Res.string.library_unfollow_error),
    style = AppTheme.typography.bodyMedium,
    color = AppTheme.colorScheme.error,
  )
}
