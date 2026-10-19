package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.design.PodcastArtwork
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.episodes_error
import software.ralf.storymile.discovery.impl.generated.resources.loading_episodes
import software.ralf.storymile.discovery.impl.generated.resources.no_episodes
import software.ralf.storymile.discovery.impl.generated.resources.play_episode
import software.ralf.storymile.discovery.impl.generated.resources.retry
import software.ralf.storymile.episodedetails.EpisodeSharedElement
import software.ralf.storymile.episodedetails.episodeSharedElement
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun Episodes(title: String?, model: EpisodesPresenter.Model, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (title != null) {
      EpisodesHeading(title)
    }
    EpisodesStatus(model)
    model.episodes.forEachIndexed { index, episode ->
      if (index > 0) {
        HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      }
      key(episode.key) { EpisodeRow(episode, index) }
    }
  }
}

@Composable
internal fun EpisodesHeading(title: String) {
  Text(
    text = title,
    modifier = Modifier.semantics { heading() },
    style = AppTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
internal fun EpisodesStatus(model: EpisodesPresenter.Model) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (model.loading) {
      Row(
        Modifier.testTag("episodes-loading"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CircularProgressIndicator(Modifier.size(24.dp))
        Text(stringResource(Res.string.loading_episodes), style = AppTheme.typography.bodyMedium)
      }
    }
    if (model.failed) {
      Text(
        stringResource(Res.string.episodes_error),
        modifier = Modifier.testTag("episodes-error"),
        style = AppTheme.typography.bodyMedium,
      )
      TextButton(onClick = model.onRetry, modifier = Modifier.testTag("episodes-retry")) {
        Text(stringResource(Res.string.retry))
      }
    } else if (!model.loading && model.episodes.isEmpty()) {
      Text(
        stringResource(Res.string.no_episodes),
        modifier = Modifier.testTag("episodes-empty"),
        style = AppTheme.typography.bodyMedium,
        color = AppTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
internal fun EpisodeRow(item: EpisodesPresenter.Episode, index: Int) {
  val details = item.details
  Row(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = item.onClick)
        .padding(vertical = 4.dp)
        .testTag("episode-$index"),
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    PodcastArtwork(
      artworkUrl = details.artworkUrl,
      modifier =
        Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Artwork)
          .testTag("episode-artwork-$index")
          .size(56.dp),
    )
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = details.episode.title,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Title)
            .testTag("episode-title-$index"),
        style = AppTheme.typography.titleSmall.copy(textMotion = TextMotion.Animated),
      )
      Text(
        text = details.podcastTitle,
        modifier =
          Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Subtitle)
            .testTag("episode-subtitle-$index"),
        style = AppTheme.typography.bodySmall.copy(textMotion = TextMotion.Animated),
        color = AppTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      details.duration?.let { duration ->
        Text(
          text = duration,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Duration)
              .testTag("episode-duration-$index"),
          style = AppTheme.typography.bodySmall.copy(textMotion = TextMotion.Animated),
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
      if (details.description.isNotBlank()) {
        Text(
          text = details.description,
          modifier =
            Modifier.episodeSharedElement(details.key, EpisodeSharedElement.Description)
              .testTag("episode-description-$index"),
          style = AppTheme.typography.bodyMedium.copy(textMotion = TextMotion.Animated),
          color = AppTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    FilledIconButton(
      onClick = {},
      enabled = false,
      modifier = Modifier.testTag("episode-play-$index"),
    ) {
      Icon(Icons.Filled.PlayArrow, stringResource(Res.string.play_episode, details.episode.title))
    }
  }
}
