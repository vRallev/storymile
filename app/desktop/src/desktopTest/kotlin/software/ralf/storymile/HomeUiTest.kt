@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test

class HomeUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `empty home adapts its illustration and invitation to phone tablet and desktop`() {
    listOf(
        DpSize(412.dp, 916.dp),
        DpSize(800.dp, 1280.dp),
        DpSize(1280.dp, 800.dp),
        DpSize(1600.dp, 900.dp),
      )
      .forEach { size ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            assertHomeControlsVisible()
            val illustration =
              onNodeWithTag("home-illustration", useUnmergedTree = true)
                .assertIsDisplayed()
                .getUnclippedBoundsInRoot()
            val prompt = onNodeWithTag("home-prompt").getUnclippedBoundsInRoot()
            if (size.width > size.height) {
              assertThat(illustration.right).isLessThanOrEqualTo(prompt.left)
            } else {
              assertThat(illustration.bottom).isLessThanOrEqualTo(prompt.top)
            }
            assertDiscoverFitsHome()
            saveScreenshot("${size.label()}-${if (darkTheme) "dark" else "light"}")
          }
        }
      }
  }

  @Test
  fun `home actions keep the empty home selected`() {
    listOf(DpSize(412.dp, 916.dp), DpSize(1280.dp, 800.dp), DpSize(1600.dp, 900.dp)).forEach { size
      ->
      uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
        listOf("home-search", "home-profile", "home-discover").forEach { tag ->
          onNodeWithTag(tag).assertIsDisplayed().performClick()
          assertHomeControlsVisible()
        }
      }
    }
  }

  @Test
  fun `discovery stays reachable around navigation breakpoints and in short windows`() {
    listOf(
        DpSize(320.dp, 1000.dp),
        DpSize(599.dp, 480.dp),
        DpSize(600.dp, 480.dp),
        DpSize(916.dp, 412.dp),
        DpSize(1199.dp, 500.dp),
        DpSize(1200.dp, 500.dp),
      )
      .forEach { size ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
          onNodeWithTag("home-search").assertIsDisplayed()
          onNodeWithTag("home-profile").assertIsDisplayed()
          onNodeWithTag("home-discover").performScrollTo().assertIsDisplayed()
          assertDiscoverFitsHome()
          onNodeWithTag("tab-home").assertIsSelected()
          saveScreenshot("${size.label()}-light")
        }
      }
  }

  @Test
  fun `large text can scroll to discovery and retains its position when the window height changes`() {
    val size = mutableStateOf(DpSize(320.dp, 500.dp))
    uiTestRule.runRobotTest(
      windowSize = DpSize(320.dp, 500.dp),
      darkTheme = false,
      contentSize = size,
      fontScale = 2f,
    ) {
      onNodeWithTag("home-discover").performScrollTo().assertIsDisplayed()
      assertDiscoverFitsHome()
      val scrollPosition = homeScrollPosition()
      assertThat(scrollPosition).isGreaterThan(0f)
      saveScreenshot("320x500-large-text-light")

      runOnIdle { size.value = DpSize(320.dp, 480.dp) }
      waitForIdle()
      assertThat(homeScrollPosition()).isCloseTo(scrollPosition, 1f)
      onNodeWithTag("home-discover").performScrollTo().assertIsDisplayed().performClick()
      onNodeWithTag("tab-home").assertIsSelected()
      assertDiscoverFitsHome()
    }
  }

  private fun ComposeUiTest.assertHomeControlsVisible() {
    listOf("home-screen", "home-welcome", "home-search", "home-profile", "home-discover").forEach {
      onNodeWithTag(it).assertIsDisplayed()
    }
    onNodeWithTag("tab-home").assertIsSelected()
  }

  private fun ComposeUiTest.assertDiscoverFitsHome() {
    val home = onNodeWithTag("home-screen").getUnclippedBoundsInRoot()
    val discover = onNodeWithTag("home-discover").getUnclippedBoundsInRoot()
    assertThat(discover.left).isGreaterThanOrEqualTo(home.left)
    assertThat(discover.right).isLessThanOrEqualTo(home.right)
    assertThat(discover.top).isGreaterThanOrEqualTo(home.top)
    assertThat(discover.bottom).isLessThanOrEqualTo(home.bottom)
  }

  private fun ComposeUiTest.homeScrollPosition(): Float =
    onNodeWithTag("home-scroll")
      .fetchSemanticsNode()
      .config[SemanticsProperties.VerticalScrollAxisRange]
      .value()

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/home-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }

  private fun DpSize.label(): String = "${width.value.toInt()}x${height.value.toInt()}"
}
