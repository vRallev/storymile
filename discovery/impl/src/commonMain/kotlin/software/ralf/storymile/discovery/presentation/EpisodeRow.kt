package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.episode_author
import software.ralf.storymile.discovery.impl.generated.resources.episode_collapsed
import software.ralf.storymile.discovery.impl.generated.resources.episode_expanded
import software.ralf.storymile.discovery.impl.generated.resources.episode_no_description
import software.ralf.storymile.discovery.impl.generated.resources.episode_published
import software.ralf.storymile.discovery.impl.generated.resources.hide_episode_details
import software.ralf.storymile.discovery.impl.generated.resources.play_episode
import software.ralf.storymile.discovery.impl.generated.resources.show_episode_details
import software.ralf.storymile.theme.AppTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun EpisodeRow(item: EpisodesPresenter.Episode, index: Int) {
  var expanded by rememberSaveable(item.key) { mutableStateOf(false) }
  val state =
    stringResource(if (expanded) Res.string.episode_expanded else Res.string.episode_collapsed)
  val clickLabel =
    stringResource(
      resource = if (expanded) Res.string.hide_episode_details else Res.string.show_episode_details,
    )
  SharedTransitionLayout(
    modifier =
      Modifier.fillMaxWidth()
        .testTag("episode-$index")
        .semantics { stateDescription = state }
        .clickable(onClickLabel = clickLabel, role = Role.Button) { expanded = !expanded }
        .padding(vertical = 4.dp),
  ) {
    AnimatedContent(
      targetState = expanded,
      modifier = Modifier.fillMaxWidth(),
      transitionSpec = {
        (fadeIn(tween(durationMillis = 200, delayMillis = 100)) togetherWith fadeOut(tween(90)))
          .using(SizeTransform { _, _ -> tween(350) })
      },
      contentAlignment = Alignment.TopStart,
    ) { showDetails ->
      val artworkModifier =
        Modifier.then(
            if (showDetails == expanded) Modifier.testTag("episode-artwork-$index") else Modifier,
          )
          .sharedElement(
            sharedContentState = rememberSharedContentState(item.key),
            animatedVisibilityScope = this@AnimatedContent,
            boundsTransform = { _, _ -> tween(350) },
          )
      if (showDetails) {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          PodcastArtwork(
            artworkUrl = item.artworkUrl,
            modifier = artworkModifier.widthIn(max = 184.dp).fillMaxWidth().aspectRatio(1f),
          )
          EpisodeHeader(item, index, Modifier.fillMaxWidth())
          EpisodeDetails(item, index)
        }
      } else {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          PodcastArtwork(item.artworkUrl, artworkModifier.size(56.dp))
          EpisodeHeader(item, index, Modifier.weight(1f))
        }
      }
    }
  }
}

@Composable
private fun EpisodeHeader(item: EpisodesPresenter.Episode, index: Int, modifier: Modifier) {
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(text = item.episode.title, style = AppTheme.typography.titleSmall)
      Text(
        text = item.podcastTitle,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      item.episode.duration?.let { duration ->
        Text(
          text =
            duration.toComponents { hours, minutes, seconds, _ ->
              if (hours > 0) {
                "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
              } else {
                "$minutes:${seconds.toString().padStart(2, '0')}"
              }
            },
          style = AppTheme.typography.bodySmall,
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    FilledIconButton(
      onClick = {},
      enabled = false,
      modifier = Modifier.testTag("episode-play-$index"),
    ) {
      Icon(Icons.Filled.PlayArrow, stringResource(Res.string.play_episode, item.episode.title))
    }
  }
}

@Composable
private fun EpisodeDetails(item: EpisodesPresenter.Episode, index: Int) {
  val description =
    remember(item.episode.description) { EpisodeDescription.plainText(item.episode.description) }
  Column(
    modifier = Modifier.fillMaxWidth().testTag("episode-details-$index"),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item.episode.author
      ?.takeIf { it.isNotBlank() }
      ?.let { author ->
        Text(
          text = stringResource(Res.string.episode_author, author),
          style = AppTheme.typography.bodySmall,
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
    item.episode.publishedAt
      ?.takeIf { it.isNotBlank() }
      ?.let { date ->
        Text(
          text = stringResource(Res.string.episode_published, date),
          style = AppTheme.typography.bodySmall,
          color = AppTheme.colorScheme.onSurfaceVariant,
        )
      }
    Text(
      text = description.ifBlank { stringResource(Res.string.episode_no_description) },
      style = AppTheme.typography.bodyMedium,
    )
  }
}
