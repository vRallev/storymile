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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
import software.ralf.storymile.episodedetails.EpisodeSharedElement
import software.ralf.storymile.episodedetails.episodeSharedElement
import software.ralf.storymile.library.FollowedShowsPresenter.Model
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_add_to_queue
import software.ralf.storymile.library.impl.generated.resources.library_no_episodes
import software.ralf.storymile.library.impl.generated.resources.library_show_episodes
import software.ralf.storymile.library.impl.generated.resources.library_shows
import software.ralf.storymile.podcast.PodcastFeed
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.util.HtmlDescription

@Composable
internal fun FollowedShowScreen(model: Model, show: PodcastFeed, listState: LazyListState) {
  if (LocalNavigationEventDispatcherOwner.current != null) {
    NavigationBackHandler(
      state = rememberNavigationEventState(NavigationEventInfo.None),
      onBackCompleted = model.onCloseDetail,
    )
  }
  Column(Modifier.fillMaxSize()) {
    TitleBar(
      title = stringResource(Res.string.library_shows),
      onBack = model.onCloseDetail,
      backButtonModifier = Modifier.testTag("library-show-back"),
    )
    Box(Modifier.weight(1f)) {
      FollowedShowDetail(model = model, show = show, listState = listState)
    }
  }
}

@Composable
internal fun FollowedShowDetail(model: Model, show: PodcastFeed, listState: LazyListState) {
  BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
    val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
    val short = maxHeight < 480.dp
    LazyColumn(
      modifier = Modifier.fillMaxSize().testTag("library-show-detail"),
      state = listState,
      contentPadding = PaddingValues(horizontal = sidePadding, vertical = 16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      item(key = "show-header") { ShowHeader(model = model, show = show, short = short) }
      item(key = "episodes-heading") {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
          text = stringResource(Res.string.library_show_episodes),
          modifier = Modifier.semantics { heading() },
          style = AppTheme.typography.titleLarge,
          fontWeight = FontWeight.SemiBold,
        )
      }
      if (show.episodes.isEmpty()) {
        item(key = "no-episodes") {
          Text(
            text = stringResource(Res.string.library_no_episodes),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
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
        SavedEpisodeRow(details = details, onClick = { model.onOpenEpisode(details) })
      }
    }
  }
}

@Composable
private fun ShowHeader(model: Model, show: PodcastFeed, short: Boolean) {
  val description = remember(show.description) { HtmlDescription.plainText(show.description) }
  BoxWithConstraints(Modifier.fillMaxWidth()) {
    if (short && maxWidth / LocalDensity.current.fontScale >= 300.dp) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
      ) {
        ShowArtwork(show = show, modifier = Modifier.size(104.dp))
        ShowMetadata(model = model, show = show, modifier = Modifier.weight(1f))
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ShowArtwork(
          show = show,
          modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).aspectRatio(1.65f),
        )
        ShowMetadata(model = model, show = show, modifier = Modifier.fillMaxWidth())
      }
    }
  }
  if (!description.isNullOrBlank()) {
    Text(
      text = description,
      modifier = Modifier.padding(top = 8.dp),
      style = AppTheme.typography.bodyMedium,
      color = AppTheme.colorScheme.onSurfaceVariant,
    )
  }
  if (model.unfollowFailed) {
    UnfollowError()
  }
}

@Composable
private fun ShowArtwork(show: PodcastFeed, modifier: Modifier) {
  PodcastArtwork(artworkUrl = show.artworkUrl, modifier = modifier, artworkId = show.feedUrl)
}

@Composable
private fun ShowMetadata(model: Model, show: PodcastFeed, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = show.title,
      modifier = Modifier.testTag("library-show-title").semantics { heading() },
      style = AppTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    if (!show.author.isNullOrBlank()) {
      Text(
        text = show.author,
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colorScheme.onSurfaceVariant,
      )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      EpisodeCount(show.episodes.size)
      Box(Modifier.weight(1f))
      ShowFollowingLabel(showCheck = true)
      ShowActions(
        show = show,
        canUnfollow = model.canUnfollow,
        onUnfollow = model.onUnfollow,
        modifier = Modifier.testTag("library-show-actions"),
      )
    }
  }
}

@Composable
private fun SavedEpisodeRow(details: EpisodeDetails, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().testTag("library-show-episode-${details.episode.id}"),
    color = Color.Transparent,
    shape = RoundedCornerShape(12.dp),
  ) {
    Row(
      modifier = Modifier.padding(vertical = 4.dp),
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PodcastArtwork(
        artworkUrl = details.artworkUrl,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Artwork).size(56.dp),
      )
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = details.episode.title,
          modifier = Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Title),
          style = AppTheme.typography.titleSmall,
        )
        details.duration?.let {
          Text(
            text = it,
            modifier = Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Duration),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      FilledIconButton(onClick = {}, enabled = false) {
        Icon(
          Icons.AutoMirrored.Filled.PlaylistAdd,
          contentDescription = stringResource(Res.string.library_add_to_queue),
        )
      }
    }
  }
  HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
