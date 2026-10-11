@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import dev.zacsweers.metro.createGraphFactory
import kotlin.math.roundToInt
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.internal.RobotInternals
import software.ralf.storymile.theme.LocalDarkThemeOverride

/**
 * Desktop integration-test fixture that renders the production application at a controlled size.
 *
 * A fresh application, root scope, Metro graph, and Compose presenter stream are created for each
 * test and destroyed after the Compose test scene closes. [withEpisodes] saves a playlist with one
 * episode and waits for playback before running the test.
 */
class DesktopUiTestRule(private val withEpisodes: Boolean = false) {
  /** Runs [block] in the phone preset, optionally overriding the system theme with [darkTheme]. */
  fun runPhoneRobotTest(darkTheme: Boolean? = null, block: ComposeUiTest.() -> Unit) {
    runRobotTest(windowSize = DesktopWindowSizes.phone, darkTheme = darkTheme, block = block)
  }

  /** Runs [block] in the tablet preset, optionally overriding the system theme with [darkTheme]. */
  fun runTabletRobotTest(darkTheme: Boolean? = null, block: ComposeUiTest.() -> Unit) {
    runRobotTest(windowSize = DesktopWindowSizes.tablet, darkTheme = darkTheme, block = block)
  }

  /**
   * Runs [block] at [windowSize], optionally overriding the system theme with [darkTheme].
   * [contentSize] changes the app's live bounds within the test surface.
   */
  fun runRobotTest(
    windowSize: DpSize,
    darkTheme: Boolean? = null,
    contentSize: State<DpSize>? = null,
    block: ComposeUiTest.() -> Unit,
  ) {
    runRobotTest(windowSize, mutableStateOf(darkTheme), contentSize, block)
  }

  /**
   * Runs [block] at [windowSize] and reacts to changes in the app's [darkTheme] override.
   * [contentSize] changes the app's live bounds within the test surface.
   */
  fun runRobotTest(
    windowSize: DpSize,
    darkTheme: State<Boolean?>,
    contentSize: State<DpSize>? = null,
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
        if (withEpisodes) {
          composeRobot<PlaylistRobot> { addPlayablePlaylist() }
        }
        setContent {
          CompositionLocalProvider(LocalDarkThemeOverride provides darkTheme.value) {
            if (contentSize == null) {
              desktopApp.renderTemplates()
            } else {
              val nativeWindow = LocalWindowInfo.current
              val density = LocalDensity.current
              val resizedWindow =
                remember(contentSize, nativeWindow, density) {
                  object : WindowInfo by nativeWindow {
                    override val containerDpSize: DpSize
                      get() = contentSize.value

                    override val containerSize: IntSize
                      get() =
                        with(density) {
                          IntSize(
                            contentSize.value.width.roundToPx(),
                            contentSize.value.height.roundToPx(),
                          )
                        }
                  }
                }
              CompositionLocalProvider(LocalWindowInfo provides resizedWindow) {
                Box(Modifier.fillMaxSize()) {
                  Box(Modifier.size(contentSize.value)) { desktopApp.renderTemplates() }
                }
              }
            }
          }
        }
        if (withEpisodes) {
          waitUntil(timeoutMillis = 5_000) {
            onAllNodes(hasTestTag("playback")).fetchSemanticsNodes().isNotEmpty()
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
