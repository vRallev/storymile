package software.ralf.storymile.util

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import software.ralf.app.platform.robot.ComposeRobot

/**
 * Dispatches input on the UI thread when [compose] provides a [ComposeUiTest].
 *
 * Pass the robot's protected `compose` provider. Other providers run [action] on the caller's
 * thread. Keep assertions and waits outside [action].
 */
fun ComposeRobot.runOnUiThread(compose: SemanticsNodeInteractionsProvider, action: () -> Unit) {
  // Compose Desktop 1.12.2's SkikoInputDispatcher.flush delivers input on the test thread.
  // Input can run layout concurrently with Molecule's AWT snapshot notifications. Dispatch
  // only input here until upstream fixes event delivery; assertions stay on the test thread.
  if (compose is ComposeUiTest) {
    compose.runOnUiThread(action)
  } else {
    action()
  }
}
