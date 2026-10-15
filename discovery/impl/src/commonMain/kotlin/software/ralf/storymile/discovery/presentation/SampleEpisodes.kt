package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.play_sample_episode
import software.ralf.storymile.discovery.impl.generated.resources.sample_episode
import software.ralf.storymile.discovery.impl.generated.resources.sample_episodes_notice
import software.ralf.storymile.theme.AppTheme

@Composable
internal fun SampleEpisodes(title: String, podcasts: List<DiscoveredPodcast>, modifier: Modifier) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
      text = title,
      modifier = Modifier.semantics { heading() },
      style = AppTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
    )
    Text(
      text = stringResource(Res.string.sample_episodes_notice),
      style = AppTheme.typography.bodyMedium,
      color = AppTheme.colorScheme.onSurfaceVariant,
    )
    podcasts.forEachIndexed { index, podcast ->
      if (index > 0) {
        HorizontalDivider(color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        PodcastArtwork(podcast, Modifier.size(56.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(Res.string.sample_episode, index + 1),
            style = AppTheme.typography.titleSmall,
          )
          Text(
            text = podcast.title,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
        FilledIconButton(onClick = {}, modifier = Modifier.testTag("episode-play-$index")) {
          Icon(Icons.Filled.PlayArrow, stringResource(Res.string.play_sample_episode, index + 1))
        }
      }
    }
  }
}
