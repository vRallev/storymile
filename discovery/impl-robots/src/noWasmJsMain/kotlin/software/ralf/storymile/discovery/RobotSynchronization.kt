@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.discovery

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.waitUntilCatching

internal fun SemanticsNodeInteractionsProvider.waitUntilCatching(
  description: String,
  assertion: () -> Unit,
) {
  if (this is ComposeUiTest) {
    // Compose waits advance rendering while the robot retries its observations.
    waitUntil(description, timeoutMillis = 5_000) {
      try {
        assertion()
        true
      } catch (_: AssertionError) {
        false
      }
    }
  } else {
    // Android's ComposeTestRule provides semantics without implementing ComposeUiTest.
    waitUntilCatching(description, timeout = 5.seconds) { assertion() }
  }
}
