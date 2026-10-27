package software.ralf.storymile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
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
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import java.awt.EventQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import software.ralf.storymile.theme.SkikoThemeEnvironment

class KmpApplicationUiTest {
  @Test
  fun `first frame precedes background startup and ready content draws under the cover`() {
    val release = CompletableDeferred<Unit>()
    val started = AtomicBoolean(false)
    val createdOnUiThread = AtomicBoolean(false)
    val application = KmpApplication {
      createdOnUiThread.set(EventQueue.isDispatchThread())
      started.set(true)
      release.await()
      StartupTestGraph(Dispatchers.Default)
    }
    try {
      runDesktopComposeUiTest(width = 400, height = 800) {
        mainClock.autoAdvance = false
        var laidOut = false
        var drawn = false
        var host by mutableIntStateOf(0)
        setContent {
          key(host) {
            application.Content(SkikoThemeEnvironment()) {
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
        onNodeWithTag("launch-animation").assertIsDisplayed()
        assertThat(started.get()).isFalse()
        mainClock.advanceTimeBy(2000)
        waitUntil { started.get() }
        assertThat(createdOnUiThread.get()).isFalse()
        onNodeWithTag("launch-animation").assertIsDisplayed()
        onNodeWithTag("destination").assertDoesNotExist()

        runOnIdle { host++ }
        mainClock.advanceTimeBy(2000)
        onNodeWithTag("launch-animation").assertIsDisplayed()
        release.complete(Unit)
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
      application.destroy()
    }
  }
}
