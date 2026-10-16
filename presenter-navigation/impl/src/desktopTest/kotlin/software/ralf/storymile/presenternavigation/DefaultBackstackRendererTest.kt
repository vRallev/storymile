@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile.presenternavigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import kotlin.test.Test

class DefaultBackstackRendererTest {
  @Test
  fun `forward and back slide short distances and fade in the layout direction`() {
    listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).forEach { layoutDirection ->
      runDesktopComposeUiTest(width = 400, height = 600) {
        val renderer = DefaultBackstackRenderer()
        var backstack by mutableStateOf(listOf(0))
        val onBack = { backstack = backstack.dropLast(1) }
        setContent {
          CompositionLocalProvider(
            LocalDensity provides Density(1f),
            LocalLayoutDirection provides layoutDirection,
          ) {
            Box(Modifier.fillMaxSize().background(Color.Black).testTag("viewport")) {
              renderer.PresenterNavDisplay(
                backstack = backstack,
                onBack = onBack,
                modifier = Modifier.fillMaxSize(),
                entryProvider = { key ->
                  NavEntry(key) {
                    Box(
                      Modifier.fillMaxSize()
                        .background(Color.White)
                        .testTag("screen-$key")
                        .clickable(onClick = onBack),
                    )
                  }
                },
              )
            }
          }
        }
        waitForIdle()
        mainClock.autoAdvance = false
        val direction = if (layoutDirection == LayoutDirection.Ltr) 1 else -1

        runOnIdle { backstack = listOf(0, 1) }
        assertTransition(entering = 1, exiting = 0, direction = direction)

        onNodeWithTag("screen-1").performClick()
        assertTransition(entering = 0, exiting = 1, direction = -direction)
        assertThat(backstack).isEqualTo(listOf(0))
      }
    }
  }

  private fun ComposeUiTest.assertTransition(entering: Int, exiting: Int, direction: Int) {
    mainClock.advanceTimeBy(80)
    assertOffset(entering, direction)
    assertOffset(exiting, -direction)
    assertFaded()

    mainClock.advanceTimeBy(96)
    assertOffset(entering, direction)
    assertFaded()

    mainClock.advanceTimeBy(500)
    assertThat(onNodeWithTag("screen-$entering").getUnclippedBoundsInRoot().left).isEqualTo(0.dp)
    onNodeWithTag("screen-$exiting").assertDoesNotExist()
  }

  private fun ComposeUiTest.assertOffset(screen: Int, direction: Int) {
    val offset = onNodeWithTag("screen-$screen").getUnclippedBoundsInRoot().left * direction
    assertThat(offset).isGreaterThan(0.dp)
    assertThat(offset).isLessThan(31.dp)
  }

  private fun ComposeUiTest.assertFaded() {
    val brightness = onNodeWithTag("viewport").captureToImage().toPixelMap()[200, 300].red
    assertThat(brightness).isGreaterThan(0f)
    assertThat(brightness).isLessThan(1f)
  }
}
