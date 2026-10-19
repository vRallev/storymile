package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isLessThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.tabs.TabsPresenter

class DownloadsUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `empty downloads fits phone tablet and desktop in both themes`() {
    listOf(
        DesktopWindowSizes.phone,
        DpSize(800.dp, 1100.dp),
        DesktopWindowSizes.tablet,
        DesktopWindowSizes.desktop,
      )
      .forEach { size ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
            assertDownloadsFits()
            val file =
              File(
                "build/reports/screenshots/downloads-${size.width.value.toInt()}-${if (darkTheme) "dark" else "light"}.png",
              )
            file.parentFile.mkdirs()
            ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
            runOnUiThread { onNodeWithTag("downloads-discover").performClick() }
            runOnUiThread { onNodeWithTag("tab-profile").performClick() }
            onNodeWithTag("tab-downloads").assertIsSelected()
          }
        }
      }
  }

  @Test
  fun `resizing shrinks the illustration and keeps downloads visible without scrolling`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.desktop, contentSize = size) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
      val tallIllustration = assertDownloadsFits()
      listOf(
          DpSize(320.dp, 480.dp),
          DpSize(480.dp, 559.dp),
          DpSize(480.dp, 680.dp),
          DpSize(480.dp, 681.dp),
          DpSize(480.dp, 600.dp),
          DpSize(900.dp, 400.dp),
          DpSize(1200.dp, 300.dp),
          DesktopWindowSizes.phone,
        )
        .forEach { next ->
          runOnIdle { size.value = next }
          val illustration = assertDownloadsFits()
          if (next == DpSize(320.dp, 480.dp)) {
            assertThat(illustration).isLessThan(tallIllustration)
          }
        }
    }
  }

  private fun ComposeUiTest.assertDownloadsFits(): Dp {
    onNodeWithTag("tab-downloads").assertIsSelected()
    onNodeWithTag("downloads-empty").assertIsDisplayed()
    val screen = onNodeWithTag("downloads-screen").assertIsDisplayed().getUnclippedBoundsInRoot()
    val descendants = hasAnyAncestor(hasTestTag("downloads-screen"))
    onAllNodes(
        descendants and SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy),
        useUnmergedTree = true,
      )
      .assertCountEquals(0)
    val textNodes =
      listOf(
          "Downloads",
          "0 episodes · 0 B",
          "Listen anywhere",
          "Download episodes to listen without a connection.",
          "Discover podcasts",
          "Use the download icon on any episode.",
        )
        .map { onNode(hasText(it) and descendants, useUnmergedTree = true) }
    textNodes.forEach { node ->
      val layouts = mutableListOf<TextLayoutResult>()
      node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
      assertThat(layouts.single().didOverflowHeight).isFalse()
    }
    val illustration = onNodeWithTag("downloads-illustration", useUnmergedTree = true)
    (textNodes + illustration + onNodeWithTag("downloads-discover")).forEach { node ->
      val bounds = node.assertIsDisplayed().getUnclippedBoundsInRoot()
      assertThat(bounds.left).isGreaterThanOrEqualTo(screen.left)
      assertThat(bounds.top).isGreaterThanOrEqualTo(screen.top)
      assertThat(bounds.right).isLessThanOrEqualTo(screen.right)
      assertThat(bounds.bottom).isLessThanOrEqualTo(screen.bottom)
    }
    val bounds = illustration.getUnclippedBoundsInRoot()
    assertThat(bounds.right - bounds.left).isGreaterThan(0.dp)
    assertThat(bounds.right - bounds.left).isEqualTo(bounds.bottom - bounds.top)
    return bounds.right - bounds.left
  }
}
