package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.episodes_error
import software.ralf.storymile.discovery.impl.generated.resources.loading_episodes
import software.ralf.storymile.discovery.impl.generated.resources.no_episodes
import software.ralf.storymile.discovery.impl.generated.resources.retry
import software.ralf.storymile.shows.component.ShowEpisodeRow
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun Episodes(
  title: String?,
  model: EpisodesPresenter.Model,
  modifier: Modifier,
  edgeToEdge: Boolean = false,
) {
  val contentModifier = Modifier.padding(horizontal = if (edgeToEdge) 16.dp else 0.dp)
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    if (title != null) {
      EpisodesHeading(title, contentModifier)
    }
    EpisodesStatus(model, contentModifier)
    model.episodes.forEachIndexed { index, episode ->
      if (index > 0) {
        HorizontalDivider(
          modifier = contentModifier,
          color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
      }
      key(episode.key) { EpisodeRow(item = episode, index = index, edgeToEdge = edgeToEdge) }
    }
  }
}

@Composable
internal fun EpisodesHeading(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    modifier = modifier.semantics { heading() },
    style = AppTheme.typography.titleLarge,
    fontWeight = FontWeight.SemiBold,
  )
}

@Composable
internal fun EpisodesStatus(model: EpisodesPresenter.Model, modifier: Modifier = Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
internal fun EpisodeRow(
  item: EpisodesPresenter.Episode,
  index: Int,
  edgeToEdge: Boolean = false,
) {
  ShowEpisodeRow(
    details = item.details,
    onClick = item.onClick,
    tagId = index.toString(),
    edgeToEdge = edgeToEdge,
  )
}
