package software.ralf.storymile.launchanimation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Reveals the application once per app scope, without replaying when its UI host is recreated. */
interface LaunchAnimation {
  /**
   * Keeps [content] composed throughout the reveal. Install inside Storymile's theme and outside
   * the application UI. Input and accessibility to the content are blocked until the reveal ends.
   */
  @Composable fun Content(modifier: Modifier = Modifier, content: @Composable () -> Unit)
}
