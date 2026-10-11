package software.ralf.storymile.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.PlaybackPresenterImpl.PlayerModel
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.speed
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.theme.AppTheme

internal object PlaybackSpeed {
  @Composable
  fun Button(
    player: PlayerModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = true,
    showLabel: Boolean = true,
  ) {
    val size = LocalScreenSize.current
    val inputEnabled = PlaybackControls.inputEnabled(expanded)
    TextButton(
      onClick = {
        if (inputEnabled) {
          onClick()
        }
      },
      modifier =
        modifier.testTag(if (expanded) "playback-speed" else "playback-bar-speed").semantics {
          if (!inputEnabled) {
            hideFromAccessibility()
          }
        },
      contentPadding =
        if (expanded) PaddingValues(horizontal = 0.dp, vertical = 8.dp)
        else ButtonDefaults.TextButtonContentPadding,
    ) {
      Column(
        modifier = Modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (expanded) 6.dp else 0.dp),
      ) {
        Label(
          text = "${player.speed}x",
          key = "speed-value",
          expanded = expanded,
          shared = size.category != ScreenSize.Category.PHONE,
          style =
            if (expanded) AppTheme.typography.headlineSmall else AppTheme.typography.titleMedium,
          color = AppTheme.colorScheme.primary,
        )
        if (showLabel) {
          Label(
            text = stringResource(Res.string.speed),
            key = "speed-label",
            expanded = expanded,
            shared = size.category != ScreenSize.Category.PHONE && size.width >= 1000.dp,
            style = if (expanded) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
            color =
              if (expanded) AppTheme.colorScheme.onSurfaceVariant else AppTheme.colorScheme.primary,
          )
        }
      }
    }
  }

  @Composable
  private fun Label(
    text: String,
    key: String,
    expanded: Boolean,
    shared: Boolean,
    style: TextStyle,
    color: Color,
  ) {
    val transition = LocalPlaybackTransition.current
    val textStyle = style.copy(color = color, textAlign = TextAlign.Center)
    val label: @Composable () -> Unit = {
      Text(
        text,
        style = textStyle,
        modifier = Modifier.testTag("playback-$key-${if (expanded) "expanded" else "collapsed"}"),
      )
    }
    if (transition == null || !shared) {
      label()
    } else {
      transition.SharedText(
        key = key,
        expanded = expanded,
        text = text,
        style = textStyle,
        modifier = Modifier.fillMaxWidth(),
        verticalArc = 80.dp,
      ) {
        label()
      }
    }
  }
}
