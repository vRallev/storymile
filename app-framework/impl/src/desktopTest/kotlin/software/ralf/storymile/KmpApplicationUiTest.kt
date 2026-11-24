package software.ralf.storymile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.storymile.theme.LocalDarkThemeOverride
import software.ralf.storymile.theme.SkikoThemeEnvironment
import software.ralf.storymile.theme.ThemeMode

class KmpApplicationUiTest {
  @Test
  fun `cover opens in the system theme and blends to the saved theme before reveal`() {
    val releaseTheme = CompletableDeferred<Unit>()
    val releaseStartup = CompletableDeferred<Unit>()
    val themeLoading = AtomicBoolean(false)
    val loadedOnUiThread = AtomicBoolean(false)
    val firstFrameDrawn = AtomicBoolean(false)
    val graphStarted = AtomicBoolean(false)
    val startupStarted = CompletableDeferred<Boolean>()
    val startup =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          startupStarted.complete(firstFrameDrawn.get())
          runBlocking { releaseStartup.await() }
        }
      }
    val application = KmpApplication {
      graphStarted.set(true)
      StartupTestGraph(
        dispatcher = Dispatchers.Default,
        appScopedInstances = setOf(startup),
        loadTheme = {
          loadedOnUiThread.set(EventQueue.isDispatchThread())
          themeLoading.set(true)
          releaseTheme.await()
          ThemeMode.DARK
        },
      )
    }
    try {
      runDesktopComposeUiTest(width = 400, height = 800) {
        mainClock.autoAdvance = false
        var laidOut = false
        var drawn = false
        var host by mutableIntStateOf(0)
        setContent {
          CompositionLocalProvider(LocalDarkThemeOverride provides false) {
            key(host) {
              application.Content(
                themeEnvironment = SkikoThemeEnvironment(),
                modifier =
                  Modifier.drawWithContent {
                    drawContent()
                    firstFrameDrawn.set(true)
                  },
              ) {
                Box(
                  Modifier.fillMaxSize()
                    .testTag("destination")
                    .background(Color.Magenta)
                    .onGloballyPositioned { laidOut = true }
                    .drawWithContent {
                      drawContent()
                      drawn = true
                    },
                )
              }
            }
          }
        }

        fun backdropLuminance(): Float = onRoot().captureToImage().toPixelMap()[0, 0].luminance()

        onNodeWithTag("launch-animation").assertIsDisplayed()
        val systemBackdrop = backdropLuminance()
        assertThat(systemBackdrop).isGreaterThan(0.5f)
        assertThat(graphStarted.get()).isFalse()
        waitUntil(timeoutMillis = 5_000) {
          mainClock.advanceTimeByFrame()
          themeLoading.get() && startupStarted.isCompleted
        }
        assertThat(loadedOnUiThread.get()).isFalse()
        assertThat(runBlocking { startupStarted.await() }).isTrue()
        assertThat(backdropLuminance()).isEqualTo(systemBackdrop)

        releaseTheme.complete(Unit)
        var blendedBackdrop = systemBackdrop
        waitUntil(timeoutMillis = 5_000) {
          mainClock.advanceTimeByFrame()
          blendedBackdrop = backdropLuminance()
          blendedBackdrop < systemBackdrop
        }
        onNodeWithTag("destination").assertDoesNotExist()

        mainClock.advanceTimeBy(1000)
        val savedBackdrop = backdropLuminance()
        assertThat(savedBackdrop).isLessThan(0.5f)
        assertThat(blendedBackdrop).isGreaterThan(savedBackdrop)
        onNodeWithTag("destination").assertDoesNotExist()
        assertThat(drawn).isFalse()

        runOnIdle { host++ }
        mainClock.advanceTimeBy(2000)
        onNodeWithTag("launch-animation").assertIsDisplayed()
        releaseStartup.complete(Unit)
        waitUntil(timeoutMillis = 5000) {
          mainClock.advanceTimeByFrame()
          drawn
        }
        assertThat(laidOut).isTrue()
        val covered = onRoot().captureToImage().toPixelMap()
        assertThat(covered[covered.width / 2, (covered.height * 0.46f).toInt()])
          .isNotEqualTo(Color.Magenta)
        mainClock.advanceTimeBy(1200)
        onNodeWithTag("launch-animation").assertDoesNotExist()
        onNodeWithTag("destination").assertIsDisplayed()
        val revealed = onRoot().captureToImage().toPixelMap()
        assertThat(revealed[0, 0]).isEqualTo(Color.Magenta)

        runOnIdle { host++ }
        mainClock.advanceTimeByFrame()
        onNodeWithTag("launch-animation").assertDoesNotExist()
        onNodeWithTag("destination").assertIsDisplayed()
      }
    } finally {
      releaseStartup.complete(Unit)
      application.destroy()
    }
  }
}
