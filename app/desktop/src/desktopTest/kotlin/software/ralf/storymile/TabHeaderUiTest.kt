package software.ralf.storymile

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.tabs.TabsPresenter

class TabHeaderUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `tab headers keep matching placement and title styles across destinations`() {
    listOf(
        DpSize(480.dp, 812.dp),
        DesktopWindowSizes.tablet,
        DesktopWindowSizes.desktop,
        DpSize(900.dp, 360.dp),
      )
      .forEach { size ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            val brandVisible = size.width < 600.dp
            val home = headerSnapshot(TabsPresenter.Tab.HOME, brandVisible)
            TabsPresenter.Tab.entries.forEach { tab ->
              composeRobot<AppShellRobot> { selectTab(tab) }
              val header = headerSnapshot(tab, brandVisible)
              assertThat(header.bounds.left, tab.name).isEqualTo(home.bounds.left)
              assertThat(header.bounds.top, tab.name).isEqualTo(home.bounds.top)
              assertThat(header.bounds.right, tab.name).isEqualTo(home.bounds.right)
              assertThat(header.controls, tab.name).isEqualTo(home.controls)
              assertThat(header.titleStyle, tab.name).isEqualTo(home.titleStyle)

              onNodeWithTag("tab-profile").assertHasClickAction()
              runOnUiThread { onNodeWithTag("tab-profile").performClick() }
              onNodeWithTag("tab-${tab.name.lowercase()}").assertIsSelected()
              val dimensions = "${size.width.value.toInt()}-${size.height.value.toInt()}"
              saveScreenshot(
                "${tab.name.lowercase()}-$dimensions-${if (darkTheme) "dark" else "light"}",
              )
            }
          }
        }
      }
  }

  private fun ComposeUiTest.headerSnapshot(
    tab: TabsPresenter.Tab,
    brandVisible: Boolean,
  ): HeaderSnapshot {
    val screenTag =
      when (tab) {
        TabsPresenter.Tab.HOME -> "home-screen"
        TabsPresenter.Tab.LIBRARY -> "library-empty"
        TabsPresenter.Tab.DOWNLOADS -> "downloads-screen"
      }
    val content = onNodeWithTag(screenTag).getUnclippedBoundsInRoot()
    val header = onNodeWithTag("tab-header").assertIsDisplayed().getUnclippedBoundsInRoot()
    val brandTags = listOf("tab-brand", "tab-brand-name", "tab-brand-logo")
    if (!brandVisible) {
      brandTags.forEach { onNodeWithTag(it, useUnmergedTree = true).assertDoesNotExist() }
    }
    val controls =
      (listOf("tab-profile") + if (brandVisible) brandTags else emptyList()).associateWith { tag ->
        onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
      }
    val title = onNodeWithTag("tab-title", useUnmergedTree = true).assertIsDisplayed()
    (controls.values + header + title.getUnclippedBoundsInRoot()).forEach { bounds ->
      assertThat(bounds.left).isGreaterThanOrEqualTo(content.left)
      assertThat(bounds.top).isGreaterThanOrEqualTo(content.top)
      assertThat(bounds.right).isLessThanOrEqualTo(content.right)
      assertThat(bounds.bottom).isLessThanOrEqualTo(content.bottom)
    }
    val layouts = mutableListOf<TextLayoutResult>()
    title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return HeaderSnapshot(header, controls, layouts.single().layoutInput.style)
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("tab-header").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/tab-header-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }

  private data class HeaderSnapshot(
    val bounds: DpRect,
    val controls: Map<String, DpRect>,
    val titleStyle: TextStyle,
  )
}
