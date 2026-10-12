package software.ralf.storymile.playback

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.PlaybackPresenterImpl.PlayerModel
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.back_fifteen
import software.ralf.storymile.playback.impl.generated.resources.bookmark
import software.ralf.storymile.playback.impl.generated.resources.download
import software.ralf.storymile.playback.impl.generated.resources.forward_thirty
import software.ralf.storymile.playback.impl.generated.resources.pause
import software.ralf.storymile.playback.impl.generated.resources.play
import software.ralf.storymile.playback.impl.generated.resources.playback_position
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.templates.LocalPlaybackTransition
import software.ralf.storymile.theme.AppTheme

internal object PlaybackControls {
  @Composable
  fun Details(player: PlayerModel, large: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
      Column(Modifier.weight(1f)) {
        Label(
          player.title,
          key = "title",
          expanded = true,
          modifier = Modifier.fillMaxWidth(),
          style = if (large) AppTheme.typography.titleLarge else AppTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(4.dp))
        Label(
          player.showTitle,
          key = "subtitle",
          expanded = true,
          modifier = Modifier.fillMaxWidth(),
          color = AppTheme.colorScheme.onSurfaceVariant,
          style = if (large) AppTheme.typography.headlineSmall else AppTheme.typography.bodyLarge,
        )
      }
      IconToggleButton(
        checked = player.isBookmarked,
        onCheckedChange = { player.onBookmark() },
        modifier = Modifier.testTag("playback-bookmark"),
      ) {
        Icon(
          if (player.isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
          stringResource(Res.string.bookmark),
          Modifier.size(28.dp),
        )
      }
      IconToggleButton(
        checked = player.isDownloaded,
        onCheckedChange = { player.onDownload() },
        modifier = Modifier.testTag("playback-download"),
      ) {
        Icon(
          if (player.isDownloaded) Icons.Default.CheckCircleOutline
          else Icons.Outlined.DownloadForOffline,
          stringResource(Res.string.download),
          Modifier.size(30.dp),
        )
      }
    }
  }

  @Composable
  fun Labels(player: PlayerModel, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier) {
      Label(
        player.title,
        key = "title",
        expanded = false,
        modifier = Modifier.fillMaxWidth(),
        style = if (compact) AppTheme.typography.titleSmall else AppTheme.typography.titleMedium,
        maxLines = 1,
      )
      Label(
        player.showTitle,
        key = "subtitle",
        expanded = false,
        modifier = Modifier.fillMaxWidth(),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
      )
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  fun Seek(
    player: PlayerModel,
    modifier: Modifier = Modifier,
    inline: Boolean = false,
    tag: String = "playback-seek",
    expanded: Boolean = true,
  ) {
    val screen = LocalScreenSize.current
    val inputEnabled = inputEnabled(expanded)
    val shared = screen.category != ScreenSize.Category.PHONE || screen.width >= 1000.dp
    val suffix = if (expanded) "expanded" else "collapsed"
    val interaction = remember { MutableInteractionSource() }
    val description = stringResource(Res.string.playback_position)
    val slider: @Composable (Modifier) -> Unit = { sliderModifier ->
      Slider(
        value = player.positionSeconds.toFloat(),
        onValueChange = {
          if (inputEnabled) {
            player.onSeek(it.roundToInt())
          }
        },
        valueRange = 0f..player.durationSeconds.coerceAtLeast(1).toFloat(),
        modifier =
          sliderModifier.testTag(tag).semantics {
            contentDescription = description
            if (!inputEnabled) {
              hideFromAccessibility()
            }
          },
        interactionSource = interaction,
        thumb = {
          SharedPart("seek-thumb", expanded, shared, zIndexInOverlay = 1f) {
            SliderDefaults.Thumb(
              interactionSource = interaction,
              modifier = Modifier.testTag("playback-seek-thumb-$suffix"),
              thumbSize = DpSize(16.dp, 16.dp),
            )
          }
        },
        track = { state ->
          SharedPart("seek-track", expanded, shared) {
            SliderDefaults.Track(
              state,
              modifier = Modifier.height(4.dp).testTag("playback-seek-track-$suffix"),
              thumbTrackGapSize = 0.dp,
              drawStopIndicator = null,
            )
          }
        },
      )
    }
    if (inline) {
      Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Time(player.positionSeconds, "position", expanded, shared)
        slider(Modifier.weight(1f))
        Time(player.durationSeconds, "duration", expanded, shared)
      }
    } else {
      Column(modifier) {
        slider(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Time(player.positionSeconds, "position", expanded, shared)
          Time(player.durationSeconds, "duration", expanded, shared)
        }
      }
    }
  }

  @Composable
  private fun Time(seconds: Int, key: String, expanded: Boolean, shared: Boolean) {
    Label(
      time(seconds),
      key = key,
      expanded = expanded,
      style = AppTheme.typography.bodyMedium,
      color = AppTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier,
      shared = shared,
      maxLines = 1,
      verticalArc =
        if (key != "duration") 0.dp
        else if (LocalScreenSize.current.width >= 1000.dp) (-56).dp else (-28).dp,
      horizontalArc =
        if (key == "duration" && LocalScreenSize.current.width < 1000.dp) (-72).dp else 0.dp,
    )
  }

  fun time(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

  @Composable
  fun inputEnabled(expanded: Boolean): Boolean {
    val transition = LocalPlaybackTransition.current
    val enabled by
      remember(transition, expanded) {
        derivedStateOf {
          transition == null || transition.fraction == if (expanded) 1f else 0f
        }
      }
    return enabled
  }

  @Composable
  fun Transport(player: PlayerModel, large: Boolean) {
    Row(
      Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Skip(player, forward = false)
      PlayPause(player, size = if (large) 112.dp else 84.dp)
      Skip(player, forward = true)
    }
  }

  @Composable
  fun Skip(player: PlayerModel, forward: Boolean, compact: Boolean = false) {
    val direction = if (forward) "forward" else "back"
    val shared = LocalScreenSize.current.width >= 1000.dp
    val expanded = !compact
    val suffix = if (expanded) "expanded" else "collapsed"
    val description =
      stringResource(if (forward) Res.string.forward_thirty else Res.string.back_fifteen)
    Box(Modifier.size(if (compact) 48.dp else 64.dp), contentAlignment = Alignment.Center) {
      SharedPart("$direction-button", expanded, shared, Modifier.matchParentSize()) {
        IconButton(
          onClick = if (forward) player.onSkipForward else player.onSkipBack,
          modifier =
            Modifier.fillMaxSize()
              .testTag("playback-${if (compact) "bar-" else ""}$direction")
              .semantics { contentDescription = description },
        ) {}
      }
      SharedPart("$direction-icon", expanded, shared) {
        Box(
          Modifier.size(if (compact) 28.dp else 48.dp).testTag("playback-$direction-icon-$suffix"),
        ) {
          Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
            if (forward) {
              Icon(Icons.Default.Forward30, null, Modifier.fillMaxSize())
            } else {
              ReplayFifteen(Modifier.fillMaxSize())
            }
          }
        }
      }
    }
  }

  @Composable
  fun PlayPause(
    player: PlayerModel,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tag: String = "playback-play-pause",
    expanded: Boolean = true,
  ) {
    val transition = LocalPlaybackTransition.current
    val button: @Composable BoxScope.() -> Unit = {
      FilledIconButton(
        onClick = player.onPlayPause,
        modifier = Modifier.fillMaxSize().testTag(tag),
        shape = CircleShape,
      ) {
        Icon(
          if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          stringResource(if (player.isPlaying) Res.string.pause else Res.string.play),
          Modifier.size(size * 0.5f),
        )
      }
    }
    if (transition == null) {
      Box(modifier.size(size), content = button)
    } else {
      transition.SharedElement(
        key = "play-pause",
        expanded = expanded,
        cornerSize = CornerSize(0.dp),
        modifier = modifier.size(size),
        content = button,
      )
    }
  }

  @Composable
  private fun Label(
    text: String,
    key: String,
    expanded: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colorScheme.onSurface,
    shared: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    verticalArc: Dp = 0.dp,
    horizontalArc: Dp = 0.dp,
  ) {
    val transition = LocalPlaybackTransition.current
    val label: @Composable BoxScope.() -> Unit = {
      Text(
        text,
        modifier = Modifier.testTag("playback-$key-${if (expanded) "expanded" else "collapsed"}"),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (transition == null || !shared) {
      Box(modifier, content = label)
    } else {
      transition.SharedText(
        key = key,
        expanded = expanded,
        text = text,
        style = style.copy(color = color),
        modifier = modifier,
        maxLines = maxLines,
        verticalArc = verticalArc,
        horizontalArc = horizontalArc,
        content = label,
      )
    }
  }

  @Composable
  private fun SharedPart(
    key: String,
    expanded: Boolean,
    shared: Boolean,
    modifier: Modifier = Modifier,
    zIndexInOverlay: Float = 0f,
    content: @Composable BoxScope.() -> Unit,
  ) {
    val transition = LocalPlaybackTransition.current
    if (transition == null || !shared) {
      Box(modifier, propagateMinConstraints = true, content = content)
    } else {
      transition.SharedElement(
        key = key,
        expanded = expanded,
        cornerSize = CornerSize(0.dp),
        modifier = modifier,
        zIndexInOverlay = zIndexInOverlay,
        content = content,
      )
    }
  }
}
