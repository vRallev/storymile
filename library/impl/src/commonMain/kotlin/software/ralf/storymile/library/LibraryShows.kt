package software.ralf.storymile.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_followed_shows
import software.ralf.storymile.library.impl.generated.resources.library_following
import software.ralf.storymile.library.impl.generated.resources.library_retry
import software.ralf.storymile.library.impl.generated.resources.library_shows_episode_count
import software.ralf.storymile.library.impl.generated.resources.library_shows_error
import software.ralf.storymile.library.impl.generated.resources.library_shows_unfollow_error
import software.ralf.storymile.shows.FollowButton
import software.ralf.storymile.shows.PodcastRow
import software.ralf.storymile.shows.ShowDetails
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun LibraryShows(model: LibraryShowsPresenter.Model, active: Boolean, modifier: Modifier) {
  BoxWithConstraints(modifier.testTag("library-page-shows")) {
    val wide = maxWidth / LocalDensity.current.fontScale >= 840.dp
    val listState = rememberLazyListState()
    val details = model.details
    if (model.loading || model.failed) {
      Column(
        modifier = Modifier.align(Alignment.Center).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (model.loading) {
          CircularProgressIndicator(Modifier.testTag("library-shows-loading"))
        } else {
          Text(stringResource(Res.string.library_shows_error))
          TextButton(onClick = model.onRetry, modifier = Modifier.testTag("library-shows-retry")) {
            Text(stringResource(Res.string.library_retry))
          }
        }
      }
    } else {
      Row(Modifier.fillMaxSize()) {
        if (wide || !model.detailOpen) {
          LazyColumn(
            modifier = Modifier.weight(1f).testTag("library-shows-list"),
            state = listState,
            contentPadding = PaddingValues(vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            item {
              Text(
                text = stringResource(Res.string.library_followed_shows),
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
                style = AppTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
              )
            }
            items(model.shows, key = { it.id }) { podcast ->
              Column {
                PodcastRow(
                  podcast = podcast,
                  onClick = { model.onSelectShow(podcast) },
                  selected = wide && details?.podcast?.id == podcast.id,
                  subtitle = if (wide) {
                    stringResource(Res.string.library_shows_episode_count, model.episodeCounts[podcast.id] ?: 0)
                  } else {
                    stringResource(Res.string.library_following)
                  },
                  trailingContent = if (wide) {
                    {
                      FollowButton(
                        followed = true,
                        enabled = podcast.id !in model.updating,
                        onClick = { model.onUnfollow(podcast.id) },
                        compact = true,
                      )
                    }
                  } else {
                    null
                  },
                )
                if (model.unfollowFailed == podcast.id) {
                  Text(
                    text = stringResource(Res.string.library_shows_unfollow_error),
                    modifier = Modifier.padding(8.dp).testTag("library-unfollow-error"),
                    color = AppTheme.colorScheme.error,
                  )
                }
                HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
              }
            }
          }
        }
        if (details != null && (wide || model.detailOpen)) {
          if (wide) {
            VerticalDivider(
              modifier = Modifier.padding(horizontal = 24.dp),
              color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
          }
          Column(Modifier.weight(1f).testTag("library-show-details")) {
            if (!wide) {
              NavigationBackHandler(
                state = rememberNavigationEventState(NavigationEventInfo.None),
                isBackEnabled = active,
                onBackCompleted = model.onBack,
              )
              TitleBar(
                title = stringResource(Res.string.library_followed_shows),
                onBack = model.onBack,
                backButtonModifier = Modifier.testTag("library-show-back"),
              )
            }
            ShowDetails(model = details, modifier = Modifier.fillMaxWidth().weight(1f), pane = wide, stackedArtwork = wide && maxHeight >= 500.dp)
          }
        }
      }
    }
  }
}
