@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThanOrEqualTo
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
        "phone" to DesktopWindowSizes.phone,
        "tablet" to DesktopWindowSizes.tablet,
        "desktop" to DesktopWindowSizes.desktop,
      )
      .forEach { (name, size) ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
            saveScreenshot("$name-${if (darkTheme) "dark" else "light"}")
            assertDownloadsFits()

            onNodeWithTag("downloads-discover").performClick()
            assertDownloadsFits()
            onNodeWithTag("downloads-profile").performClick()
            assertDownloadsFits()
          }
        }
      }
  }

  @Test
  fun `short windows keep every downloads message and action reachable`() {
    listOf(DpSize(671.dp, 500.dp), DpSize(672.dp, 500.dp), DpSize(1200.dp, 300.dp)).forEach { size
      ->
      val contentSize = mutableStateOf(DpSize(size.width, 600.dp))
      uiTestRule.runRobotTest(
        windowSize = contentSize.value,
        darkTheme = false,
        contentSize = contentSize,
      ) {
        composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
        runOnIdle { contentSize.value = size }
        assertMessagesReachable()
        saveScreenshot("short-${size.width.value.toInt()}x${size.height.value.toInt()}")
      }
    }
  }

  @Test
  fun `larger fonts keep downloads instructions and discovery reachable`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DesktopWindowSizes.desktop)
      .forEach { size ->
        listOf(1.5f, 2f).forEach { fontScale ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = false, fontScale = fontScale) {
            composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
            assertMessagesReachable()
            saveScreenshot(
              "font-$fontScale-${size.width.value.toInt()}x${size.height.value.toInt()}",
            )
          }
        }
      }
  }

  @Test
  fun `downloads stays selected and readable across window changes`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(1440.dp, 1440.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.DOWNLOADS) }
      listOf(
          DpSize(320.dp, 1100.dp),
          DpSize(599.dp, 840.dp),
          DpSize(600.dp, 840.dp),
          DesktopWindowSizes.tablet,
          DpSize(1199.dp, 900.dp),
          DpSize(1200.dp, 900.dp),
          DpSize(1200.dp, 500.dp),
          DesktopWindowSizes.desktop,
          DesktopWindowSizes.phone,
        )
        .forEach { next ->
          runOnIdle { size.value = next }
          assertDownloadsFits()
          saveScreenshot("resize-${next.width.value.toInt()}x${next.height.value.toInt()}")
        }
    }
  }

  private fun ComposeUiTest.assertDownloadsFits() {
    composeRobot<AppShellRobot> { seeSelectedTab(TabsPresenter.Tab.DOWNLOADS) }
    val shell = onNodeWithTag("app-shell").getUnclippedBoundsInRoot()
    val screen = onNodeWithTag("downloads-screen").assertIsDisplayed().getUnclippedBoundsInRoot()
    assertWithin(screen, shell)
    assertThat(screen.bottom)
      .isLessThanOrEqualTo(onNodeWithTag("playback").getUnclippedBoundsInRoot().top)

    val title = downloadsText("Downloads").assertTextFits(screen)
    val count = downloadsText("0 episodes · 0 B").assertTextFits(screen)
    val illustration =
      onNodeWithTag("downloads-illustration", useUnmergedTree = true)
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()
    val headline = downloadsText("Listen anywhere").assertTextFits(screen)
    val body =
      downloadsText("Download episodes to listen without a connection.").assertTextFits(screen)
    val button = onNodeWithTag("downloads-discover").assertIsDisplayed().getUnclippedBoundsInRoot()
    downloadsText("Discover podcasts").assertTextFits(button)
    val helper = downloadsText("Use the download icon on any episode.").assertTextFits(screen)
    assertWithin(illustration, screen)
    assertWithin(button, screen)

    assertThat(count.top).isGreaterThanOrEqualTo(title.bottom)
    assertThat(headline.top).isGreaterThanOrEqualTo(count.bottom)
    assertThat(body.top).isGreaterThanOrEqualTo(headline.bottom)
    assertThat(button.top).isGreaterThanOrEqualTo(body.bottom)
    assertThat(helper.top).isGreaterThanOrEqualTo(button.bottom)

    val empty = onNodeWithTag("downloads-empty").getUnclippedBoundsInRoot()
    listOf(body, button, helper).forEach { bounds ->
      assertThat(bounds.centerX()).isCloseTo(headline.centerX(), 1f)
    }
    if (illustration.right <= headline.left) {
      listOf(headline, body, button, helper).forEach { bounds ->
        assertThat(bounds.left).isGreaterThanOrEqualTo(illustration.right)
      }
      assertThat(illustration.centerY())
        .isCloseTo((headline.top.value + helper.bottom.value) / 2, 1f)
    } else {
      assertThat(illustration.top).isGreaterThanOrEqualTo(count.bottom)
      assertThat(headline.top).isGreaterThanOrEqualTo(illustration.bottom)
      assertThat(illustration.centerX()).isCloseTo(empty.centerX(), 1f)
      assertThat(headline.centerX()).isCloseTo(empty.centerX(), 1f)
    }
  }

  private fun DpRect.centerX(): Float = (left.value + right.value) / 2

  private fun DpRect.centerY(): Float = (top.value + bottom.value) / 2

  private fun ComposeUiTest.assertMessagesReachable() {
    val screen = onNodeWithTag("downloads-screen").getUnclippedBoundsInRoot()
    listOf(
        "Downloads",
        "0 episodes · 0 B",
        "Listen anywhere",
        "Download episodes to listen without a connection.",
        "Discover podcasts",
        "Use the download icon on any episode.",
      )
      .forEach { text -> downloadsText(text).performScrollTo().assertTextFits(screen) }
    val button = onNodeWithTag("downloads-discover").performScrollTo().assertIsDisplayed()
    assertWithin(button.getUnclippedBoundsInRoot(), screen)
    button.performClick()
    onNodeWithTag("tab-downloads").assertIsSelected()
    onNodeWithTag("downloads-empty").assertIsDisplayed()
  }

  private fun ComposeUiTest.downloadsText(text: String): SemanticsNodeInteraction =
    onNode(
      hasText(text) and hasAnyAncestor(hasTestTag("downloads-screen")),
      useUnmergedTree = true,
    )

  private fun SemanticsNodeInteraction.assertTextFits(container: DpRect): DpRect {
    val bounds = assertIsDisplayed().getUnclippedBoundsInRoot()
    assertWithin(bounds, container)
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    val layout = layouts.single()
    assertThat(layout.didOverflowHeight, "Text overflows at $bounds").isFalse()
    // Skiko's paragraph can retain the available width for wrap-content text.
    repeat(layout.lineCount) { line ->
      assertThat(layout.isLineEllipsized(line)).isFalse()
      assertThat(layout.getLineRight(line) - layout.getLineLeft(line))
        .isLessThanOrEqualTo(layout.size.width + 1f)
    }
    return bounds
  }

  private fun assertWithin(bounds: DpRect, container: DpRect) {
    assertThat(bounds.left).isGreaterThanOrEqualTo(container.left)
    assertThat(bounds.top).isGreaterThanOrEqualTo(container.top)
    assertThat(bounds.right).isLessThanOrEqualTo(container.right)
    assertThat(bounds.bottom).isLessThanOrEqualTo(container.bottom)
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/downloads-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(
      onNodeWithTag("app-shell").captureToImage().toAwtImage(),
      "png",
      file,
    )
  }
}
