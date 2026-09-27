@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import dev.zacsweers.metro.createGraphFactory
import kotlin.math.roundToInt
import software.ralf.app.platform.robot.internal.RobotInternals
import software.ralf.storymile.theme.LocalDarkThemeOverride

/**
 * Desktop integration-test fixture that renders the production application at a controlled size.
 *
 * A fresh application, root scope, Metro graph, and Compose presenter stream are created for each
 * test and destroyed after the Compose test scene closes.
 */
class DesktopUiTestRule {
  /** Runs [block] in the phone preset, optionally overriding the system theme with [darkTheme]. */
  fun runPhoneRobotTest(darkTheme: Boolean? = null, block: ComposeUiTest.() -> Unit) {
    runRobotTest(windowSize = DesktopWindowSizes.phone, darkTheme = darkTheme, block = block)
  }

  /** Runs [block] in the tablet preset, optionally overriding the system theme with [darkTheme]. */
  fun runTabletRobotTest(darkTheme: Boolean? = null, block: ComposeUiTest.() -> Unit) {
    runRobotTest(windowSize = DesktopWindowSizes.tablet, darkTheme = darkTheme, block = block)
  }

  private fun runRobotTest(
    windowSize: DpSize,
    darkTheme: Boolean?,
    block: ComposeUiTest.() -> Unit,
  ) {
    val desktopApp = DesktopApp {
      createGraphFactory<TestDesktopAppGraph.Factory>().create(it)
    }
    RobotInternals.setRootScopeProvider(desktopApp)

    try {
      runDesktopComposeUiTest(
        width = windowSize.width.value.roundToInt(),
        height = windowSize.height.value.roundToInt(),
      ) {
        setContent {
          CompositionLocalProvider(LocalDarkThemeOverride provides darkTheme) {
            desktopApp.renderTemplates()
          }
        }
        block()
      }
    } finally {
      RobotInternals.setRootScopeProvider(null)
      desktopApp.destroy()
    }
  }
}
