package software.ralf.storymile.shows

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.shows.generated.resources.Res
import software.ralf.storymile.shows.generated.resources.follow
import software.ralf.storymile.shows.generated.resources.following
import software.ralf.storymile.theme.AppTheme

/** Animated follow control. The caller owns persistence and retry behavior. */
@Composable
fun FollowButton(
  followed: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  compact: Boolean = false,
  identity: Any? = null,
) {
  val buttonColor by
    animateColorAsState(
      targetValue =
        if (followed) {
          AppTheme.colorScheme.primaryContainer
        } else {
          AppTheme.colorScheme.primary
        },
    )
  val contentColor by
    animateColorAsState(
      targetValue =
        if (followed) {
          AppTheme.colorScheme.onPrimaryContainer
        } else {
          AppTheme.colorScheme.onPrimary
        },
    )
  Button(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.animateContentSize(),
    colors = ButtonDefaults.buttonColors(containerColor = buttonColor, contentColor = contentColor),
    contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 24.dp, vertical = 12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      key(identity) { FollowIcon(followed = followed) }
      Spacer(Modifier.width(8.dp))
      AnimatedContent(targetState = followed, label = "Follow label") { currentFollowed ->
        Text(stringResource(if (currentFollowed) Res.string.following else Res.string.follow))
      }
    }
  }
}
