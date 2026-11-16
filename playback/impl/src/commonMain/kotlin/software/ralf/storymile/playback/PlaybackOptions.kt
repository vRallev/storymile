package software.ralf.storymile.playback

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.PlaybackPresenterImpl.PlayerModel
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.close
import software.ralf.storymile.playback.impl.generated.resources.episode_info
import software.ralf.storymile.playback.impl.generated.resources.playback_speed
import software.ralf.storymile.playback.impl.generated.resources.queue
import software.ralf.storymile.playback.impl.generated.resources.queue_empty
import software.ralf.storymile.playback.impl.generated.resources.sample_description
import software.ralf.storymile.playback.impl.generated.resources.up_next
import software.ralf.storymile.templates.LocalPlaybackTransition
import software.ralf.storymile.theme.AppTheme

internal object PlaybackOptions {
  @Composable
  fun Actions(player: PlayerModel, onSelect: (Panel) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      PlaybackSpeed.Button(
        player,
        onClick = { onSelect(Panel.SPEED) },
        modifier = Modifier.weight(1f),
      )
      TextButton(
        onClick = { onSelect(Panel.QUEUE) },
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        modifier = Modifier.weight(1f).testTag("playback-queue"),
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(32.dp))
          Text(
            text = stringResource(Res.string.queue),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }

  @Composable
  fun Dialog(panel: Panel?, player: PlayerModel, expanded: Boolean, onDismiss: () -> Unit) {
    val transition = LocalPlaybackTransition.current
    val visible by
      remember(transition, expanded) {
        derivedStateOf {
          transition == null || if (expanded) transition.fraction > 0f else transition.fraction < 1f
        }
      }
    LaunchedEffect(visible) {
      if (!visible) {
        onDismiss()
      }
    }
    if (panel == null || !visible) {
      return
    }
    BasicAlertDialog(onDismissRequest = onDismiss) {
      Surface(
        shape = AlertDialogDefaults.shape,
        color = AlertDialogDefaults.containerColor,
        tonalElevation = AlertDialogDefaults.TonalElevation,
      ) {
        Column(Modifier.padding(24.dp)) {
          Text(
            text = stringResource(panel.title),
            modifier = Modifier.padding(bottom = 16.dp),
            color = AlertDialogDefaults.titleContentColor,
            style = AppTheme.typography.headlineSmall,
          )
          CompositionLocalProvider(
            LocalContentColor provides AlertDialogDefaults.textContentColor,
          ) {
            ProvideTextStyle(AppTheme.typography.bodyMedium) {
              LazyColumn(
                Modifier.weight(1f, fill = false).padding(bottom = 24.dp).heightIn(max = 420.dp),
              ) {
                content(panel = panel, player = player, onDismiss = onDismiss)
              }
            }
          }
          TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(Res.string.close))
          }
        }
      }
    }
  }

  private fun LazyListScope.content(panel: Panel, player: PlayerModel, onDismiss: () -> Unit) {
    when (panel) {
      Panel.SPEED ->
        listOf(0.75f, 1f, 1.2f, 1.5f, 2f).forEach { speed ->
          item {
            Choice(title = "${speed}x", tag = "speed-$speed", selected = player.speed == speed) {
              player.onSpeed(speed)
              onDismiss()
            }
          }
        }
      Panel.QUEUE -> {
        if (player.queue.isEmpty()) {
          item { Text(stringResource(Res.string.queue_empty)) }
        }
        itemsIndexed(player.queue) { index, episode ->
          Choice(
            title = episode.title,
            tag = "episode-$index",
            supporting = episode.showTitle,
          ) {
            player.onEpisode(index)
            onDismiss()
          }
        }
      }
      Panel.INFO -> {
        item {
          Text(player.title, style = AppTheme.typography.titleMedium)
          Text(player.showTitle, modifier = Modifier.padding(vertical = 8.dp))
          val description =
            if (player.isPreview) stringResource(Res.string.sample_description)
            else player.description
          description?.let { Text(it) }
        }
      }
    }
  }

  @Composable
  private fun Choice(
    title: String,
    tag: String,
    selected: Boolean? = null,
    supporting: String? = null,
    onClick: () -> Unit,
  ) {
    ListItem(
      modifier =
        Modifier.fillMaxWidth().testTag("playback-option-$tag").clickable(onClick = onClick),
      headlineContent = { Text(title) },
      supportingContent = supporting?.let { { Text(it) } },
      trailingContent = selected?.let { { RadioButton(selected = it, onClick = null) } },
      colors = ListItemDefaults.colors(containerColor = AppTheme.colorScheme.surfaceContainerHigh),
    )
  }

  enum class Panel(val title: StringResource) {
    SPEED(Res.string.playback_speed),
    QUEUE(Res.string.up_next),
    INFO(Res.string.episode_info),
  }
}
