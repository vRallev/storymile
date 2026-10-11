package software.ralf.storymile.playback

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.PlaybackPresenterImpl.PlayerModel
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.chapter_number
import software.ralf.storymile.playback.impl.generated.resources.chapters
import software.ralf.storymile.playback.impl.generated.resources.close
import software.ralf.storymile.playback.impl.generated.resources.episode_info
import software.ralf.storymile.playback.impl.generated.resources.playback_speed
import software.ralf.storymile.playback.impl.generated.resources.queue
import software.ralf.storymile.playback.impl.generated.resources.queue_empty
import software.ralf.storymile.playback.impl.generated.resources.sample_description
import software.ralf.storymile.playback.impl.generated.resources.sleep
import software.ralf.storymile.playback.impl.generated.resources.sleep_minutes
import software.ralf.storymile.playback.impl.generated.resources.sleep_off
import software.ralf.storymile.playback.impl.generated.resources.sleep_timer
import software.ralf.storymile.playback.impl.generated.resources.speed
import software.ralf.storymile.playback.impl.generated.resources.up_next
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
      listOf(Panel.SLEEP, Panel.QUEUE, Panel.CHAPTERS).forEach { panel ->
        TextButton(
          onClick = { onSelect(panel) },
          contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
          modifier = Modifier.weight(1f).testTag("playback-${panel.name.lowercase()}"),
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            when (panel) {
              Panel.SLEEP -> Icon(Icons.Default.DarkMode, null, Modifier.size(32.dp))
              Panel.QUEUE -> Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(32.dp))
              Panel.CHAPTERS ->
                Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(32.dp))
              Panel.SPEED,
              Panel.INFO -> Unit
            }
            Text(
              if (panel == Panel.SLEEP && player.sleepMinutes != null)
                stringResource(Res.string.sleep_minutes, player.sleepMinutes)
              else stringResource(panel.label),
              style = AppTheme.typography.bodyMedium,
              color = AppTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    }
  }

  @Composable
  fun Chapter(player: PlayerModel, onClick: () -> Unit) {
    OutlinedCard(
      onClick = onClick,
      modifier = Modifier.fillMaxWidth().testTag("playback-current-chapter"),
      shape = RoundedCornerShape(20.dp),
      border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
      colors =
        CardDefaults.outlinedCardColors(
          containerColor = AppTheme.colorScheme.surfaceContainerLowest,
        ),
    ) {
      ListItem(
        headlineContent = {
          Text(
            player.chapters[player.activeChapterIndex].title,
            style = AppTheme.typography.titleMedium,
          )
        },
        supportingContent = {
          Text(
            stringResource(
              Res.string.chapter_number,
              player.activeChapterIndex + 1,
              player.chapters.size,
            ),
          )
        },
        leadingContent = {
          Icon(
            Icons.Default.GraphicEq,
            null,
            Modifier.size(32.dp),
            tint = AppTheme.colorScheme.primary,
          )
        },
        trailingContent = {
          Icon(Icons.AutoMirrored.Filled.NavigateNext, null, tint = AppTheme.colorScheme.primary)
        },
        colors =
          ListItemDefaults.colors(containerColor = AppTheme.colorScheme.surfaceContainerLowest),
      )
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
    AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text(stringResource(panel.title)) },
      text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
          Content(panel, player, onDismiss)
        }
      },
      confirmButton = {
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
      },
    )
  }

  @Composable
  private fun Content(panel: Panel, player: PlayerModel, onDismiss: () -> Unit) {
    when (panel) {
      Panel.SPEED ->
        listOf(0.75f, 1f, 1.2f, 1.5f, 2f).forEach { speed ->
          Choice("${speed}x", "speed-$speed", selected = player.speed == speed) {
            player.onSpeed(speed)
            onDismiss()
          }
        }
      Panel.SLEEP ->
        listOf(null, 15, 30, 45, 60).forEach { minutes ->
          Choice(
            if (minutes == null) stringResource(Res.string.sleep_off)
            else stringResource(Res.string.sleep_minutes, minutes),
            "sleep-${minutes ?: "off"}",
            selected = player.sleepMinutes == minutes,
          ) {
            player.onSleep(minutes)
            onDismiss()
          }
        }
      Panel.CHAPTERS ->
        player.chapters.forEachIndexed { index, chapter ->
          Choice(
            chapter.title,
            "chapter-$index",
            selected = player.activeChapterIndex == index,
            supporting = PlaybackControls.time(chapter.startSeconds),
          ) {
            player.onChapter(index)
            onDismiss()
          }
        }
      Panel.QUEUE -> {
        if (player.queue.isEmpty()) {
          Text(stringResource(Res.string.queue_empty))
        }
        player.queue.forEachIndexed { index, episode ->
          Choice(episode.title, "episode-$index", supporting = episode.showTitle) {
            player.onEpisode(index)
            onDismiss()
          }
        }
      }
      Panel.INFO -> {
        Text(player.title, style = AppTheme.typography.titleMedium)
        Text(player.showTitle, modifier = Modifier.padding(vertical = 8.dp))
        Text(stringResource(Res.string.sample_description))
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

  enum class Panel {
    SPEED,
    SLEEP,
    QUEUE,
    CHAPTERS,
    INFO;

    val label
      get() =
        when (this) {
          SPEED -> Res.string.speed
          SLEEP -> Res.string.sleep
          QUEUE -> Res.string.queue
          CHAPTERS -> Res.string.chapters
          INFO -> Res.string.episode_info
        }

    val title
      get() =
        when (this) {
          SPEED -> Res.string.playback_speed
          SLEEP -> Res.string.sleep_timer
          QUEUE -> Res.string.up_next
          else -> label
        }
  }
}
