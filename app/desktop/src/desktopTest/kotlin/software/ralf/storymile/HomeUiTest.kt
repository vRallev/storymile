@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
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
            assertHomeFitsWithoutScrolling()
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
        listOf("home-search", "tab-profile", "home-discover").forEach { tag ->
          onNodeWithTag(tag).assertIsDisplayed()
          runOnUiThread { onNodeWithTag(tag).performClick() }
          assertHomeFitsWithoutScrolling()
        }
      }
    }
  }

  @Test
  fun `home content fits small phones and short windows without scrolling`() {
    listOf(
        DpSize(480.dp, 812.dp),
        DpSize(412.dp, 780.dp),
        DpSize(360.dp, 640.dp),
        DpSize(320.dp, 1000.dp),
        DpSize(599.dp, 480.dp),
        DpSize(600.dp, 480.dp),
        DpSize(900.dp, 360.dp),
        DpSize(916.dp, 412.dp),
        DpSize(1199.dp, 500.dp),
        DpSize(1200.dp, 500.dp),
      )
      .forEach { size ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
          assertHomeFitsWithoutScrolling()
          saveScreenshot("${size.label()}-light")
        }
      }
  }

  @Test
  fun `the illustration shrinks as the window gets shorter while all content stays visible`() {
    val size = mutableStateOf(DpSize(412.dp, 916.dp))
    uiTestRule.runRobotTest(
      windowSize = size.value,
      darkTheme = false,
      contentSize = size,
    ) {
      assertHomeFitsWithoutScrolling()
      val originalImageHeight = illustrationBounds().height
      var imageHeight = originalImageHeight
      listOf(780.dp, 640.dp).forEach { height ->
        runOnIdle { size.value = DpSize(412.dp, height) }
        waitForIdle()
        assertHomeFitsWithoutScrolling()
        val resizedImageHeight = illustrationBounds().height
        assertThat(resizedImageHeight).isLessThanOrEqualTo(imageHeight)
        imageHeight = resizedImageHeight
        saveScreenshot("${size.value.label()}-resized-light")
      }
      assertThat(imageHeight).isLessThan(originalImageHeight)
    }
  }

  @Test
  fun `large text stays fully visible without scrolling`() {
    listOf(DpSize(480.dp, 1000.dp), DpSize(800.dp, 1280.dp)).forEach { size ->
      uiTestRule.runRobotTest(windowSize = size, darkTheme = false, fontScale = 2f) {
        assertHomeFitsWithoutScrolling()
        saveScreenshot("${size.label()}-large-text-light")
      }
    }
  }

  private fun ComposeUiTest.assertHomeFitsWithoutScrolling() {
    val home = onNodeWithTag("home-screen").assertIsDisplayed().getUnclippedBoundsInRoot()
    val insideHome = hasTestTag("home-screen") or hasAnyAncestor(hasTestTag("home-screen"))
    onAllNodes(
        insideHome and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange),
        useUnmergedTree = true,
      )
      .assertCountEquals(0)
    listOf("tab-title", "home-search", "tab-profile", "home-discover", "home-illustration")
      .forEach { tag ->
        onNodeWithTag(tag, useUnmergedTree = true).assertFullyInside(home)
      }
    listOf(
        "Welcome to Storymile",
        "Podcasts and episodes",
        "Find your first podcast",
        "Explore topics and find a story worth listening to.",
        "Discover podcasts",
        "Listen without an account",
      )
      .forEach { text ->
        val node = onNode(hasText(text) and insideHome, useUnmergedTree = true)
        node.assertFullyInside(home)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts, text).hasSize(1)
        val layout = layouts.single()
        val description =
          "$text: size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, " +
            "constraints=${layout.layoutInput.constraints}, widthOverflow=${layout.didOverflowWidth}, " +
            "heightOverflow=${layout.didOverflowHeight}, lines=${layout.lineCount}"
        assertThat(layout.didOverflowHeight, description).isFalse()
        repeat(layout.lineCount) { line ->
          // The paragraph can keep its original maximum width after the text wraps to content.
          val lineWidth = layout.getLineRight(line) - layout.getLineLeft(line)
          assertThat(lineWidth, "$description, line=$line")
            .isLessThanOrEqualTo(layout.size.width + 1f)
          assertThat(layout.isLineEllipsized(line), description).isFalse()
        }
      }
    onNodeWithTag("tab-home").assertIsSelected()
  }

  private fun SemanticsNodeInteraction.assertFullyInside(parent: DpRect) {
    val bounds = assertIsDisplayed().getUnclippedBoundsInRoot()
    assertThat(bounds.width).isGreaterThan(0.dp)
    assertThat(bounds.height).isGreaterThan(0.dp)
    assertThat(bounds.left).isGreaterThanOrEqualTo(parent.left)
    assertThat(bounds.right).isLessThanOrEqualTo(parent.right)
    assertThat(bounds.top).isGreaterThanOrEqualTo(parent.top)
    assertThat(bounds.bottom).isLessThanOrEqualTo(parent.bottom)
  }

  private fun ComposeUiTest.illustrationBounds(): DpRect =
    onNodeWithTag("home-illustration", useUnmergedTree = true).getUnclippedBoundsInRoot()

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/home-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }

  private fun DpSize.label(): String = "${width.value.toInt()}x${height.value.toInt()}"
}
