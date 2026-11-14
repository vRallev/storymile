package software.ralf.storymile.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.coil.PodcastArtwork
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.episodedetails.EpisodeDetails
import software.ralf.storymile.library.FollowedShowsPresenter.Model
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_following
import software.ralf.storymile.library.impl.generated.resources.library_hide_description
import software.ralf.storymile.library.impl.generated.resources.library_no_description
import software.ralf.storymile.library.impl.generated.resources.library_no_episodes
import software.ralf.storymile.library.impl.generated.resources.library_show_description
import software.ralf.storymile.library.impl.generated.resources.library_show_episodes
import software.ralf.storymile.library.impl.generated.resources.library_shows
import software.ralf.storymile.library.impl.generated.resources.library_unfollow_error
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.shows.component.ShowEpisodeRow
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.util.HtmlDescription

@Composable
internal fun FollowedShowDetail(
  model: Model,
  show: PodcastFeed,
  listState: LazyListState,
  split: Boolean,
) {
  var descriptionExpanded by rememberSaveable(show.feedUrl) { mutableStateOf(false) }
  if (!split && LocalNavigationEventDispatcherOwner.current != null) {
    NavigationBackHandler(
      state = rememberNavigationEventState(NavigationEventInfo.None),
      onBackCompleted = model.onCloseDetail,
    )
  }
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val edgeToEdgeEpisodes = !split && maxWidth < 600.dp
    val contentModifier = Modifier.padding(horizontal = if (edgeToEdgeEpisodes) 16.dp else 0.dp)
    Column(Modifier.fillMaxSize()) {
      if (!split) {
        TitleBar(
          title = stringResource(Res.string.library_shows),
          onBack = model.onCloseDetail,
          backButtonModifier = Modifier.testTag("library-show-back"),
        )
      }
      LazyColumn(
        modifier =
          Modifier.weight(1f)
            .fillMaxWidth()
            .padding(horizontal = if (edgeToEdgeEpisodes) 0.dp else 32.dp)
            .testTag("library-show-detail"),
        state = listState,
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        item(key = "show-header") {
          Column(contentModifier) {
            ShowHeader(
              model = model,
              show = show,
              split = split,
              descriptionExpanded = descriptionExpanded,
              onToggleDescription = { descriptionExpanded = !descriptionExpanded },
            )
            AnimatedVisibility(visible = descriptionExpanded) {
              val description =
                remember(show.description) { HtmlDescription.plainText(show.description) }
              Text(
                text = description ?: stringResource(Res.string.library_no_description),
                modifier =
                  Modifier.fillMaxWidth().padding(top = 16.dp).testTag("library-show-description"),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        item(key = "episodes-divider") {
          HorizontalDivider(
            modifier = contentModifier,
            color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
          )
        }
        item(key = "episodes-heading") {
          Column(contentModifier) {
            Text(
              text = stringResource(Res.string.library_show_episodes),
              modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
              style = AppTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
            if (show.episodes.isEmpty()) {
              Text(
                text = stringResource(Res.string.library_no_episodes),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
        items(show.episodes, key = { "episode-${it.id}" }) { episode ->
          val details =
            remember(show, episode) {
              EpisodeDetails(
                feedUrl = show.feedUrl,
                podcastTitle = show.title,
                artworkUrl = episode.artworkUrl ?: show.artworkUrl,
                episode = episode,
              )
            }
          ShowEpisodeRow(
            details = details,
            onClick = { model.onOpenEpisode(details) },
            tagId = episode.id,
            edgeToEdge = edgeToEdgeEpisodes,
            tagPrefix = "library-show-episode",
          )
        }
      }
    }
  }
}

@Composable
private fun ShowHeader(
  model: Model,
  show: PodcastFeed,
  split: Boolean,
  descriptionExpanded: Boolean,
  onToggleDescription: () -> Unit,
) {
  val metadata: @Composable (Modifier) -> Unit = { modifier ->
    ShowMetadata(
      model = model,
      show = show,
      split = split,
      descriptionExpanded = descriptionExpanded,
      onToggleDescription = onToggleDescription,
      modifier = modifier,
    )
  }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    val readableWidth = maxWidth / LocalDensity.current.fontScale
    if (readableWidth >= (if (split) 320.dp else 440.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(Modifier.weight(if (split) 0.6f else 0.8f)) {
          ShowArtwork(show = show, modifier = Modifier.widthIn(max = 240.dp))
        }
        metadata(Modifier.weight(if (split) 1.4f else 1.2f))
      }
    } else {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        ShowArtwork(show = show, modifier = Modifier.widthIn(max = 320.dp))
        metadata(Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun ShowArtwork(show: PodcastFeed, modifier: Modifier) {
  PodcastArtwork(
    artworkUrl = show.artworkUrl,
    artworkId = show.feedUrl,
    modifier = modifier.fillMaxWidth().aspectRatio(1f).testTag("library-show-artwork"),
  )
}

@Composable
private fun ShowMetadata(
  model: Model,
  show: PodcastFeed,
  split: Boolean,
  descriptionExpanded: Boolean,
  onToggleDescription: () -> Unit,
  modifier: Modifier,
) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = show.title,
          modifier =
            Modifier.weight(weight = 1f, fill = false).testTag("library-show-title").semantics {
              heading()
            },
          style = AppTheme.typography.headlineSmall,
          fontWeight = FontWeight.Bold,
        )
        IconToggleButton(
          checked = descriptionExpanded,
          onCheckedChange = { onToggleDescription() },
          modifier = Modifier.testTag("library-show-info"),
        ) {
          Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription =
              stringResource(
                if (descriptionExpanded) Res.string.library_hide_description
                else Res.string.library_show_description,
              ),
          )
        }
      }
      show.author?.let { author ->
        Text(
          text = author,
          style = AppTheme.typography.bodyLarge,
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Button(
      onClick = { model.onUnfollow(show.feedUrl) },
      enabled = model.canUnfollow,
      modifier = Modifier.widthIn(min = 180.dp).testTag("library-show-follow"),
      colors =
        ButtonDefaults.buttonColors(
          containerColor = AppTheme.colorScheme.primaryContainer,
          contentColor = AppTheme.colorScheme.onPrimaryContainer,
        ),
      contentPadding = PaddingValues(horizontal = if (split) 12.dp else 24.dp, vertical = 12.dp),
    ) {
      Icon(imageVector = Icons.Default.Check, contentDescription = null)
      Spacer(Modifier.width(8.dp))
      Text(stringResource(Res.string.library_following))
    }
    if (model.unfollowFailed) {
      Text(
        text = stringResource(Res.string.library_unfollow_error),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colorScheme.error,
      )
    }
  }
}
