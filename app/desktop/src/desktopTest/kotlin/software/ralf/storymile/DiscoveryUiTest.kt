package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.robot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot
import software.ralf.storymile.tabs.TabsPresenter.Tab

class DiscoveryUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `browse topics search show preview and back retain context while home resets it`() {
    uiTestRule.runPhoneRobotTest(darkTheme = false) {
      composeRobot<DiscoveryRobot> {
        discover()
        seePodcast("quiet-current")
        selectNature()
        openPodcast("quiet-current")
      }
      composeRobot<ShowRobot> {
        seeShow()
        pressPreviewActions()
        back()
      }

      composeRobot<DiscoveryRobot> {
        seePodcast("field-notes")
        backFromCatalog()
        search("Field")
        openPodcast("field-notes")
      }
      composeRobot<ShowRobot> {
        seeShow()
        back()
      }

      composeRobot<DiscoveryRobot> {
        seeQuery("Field")
        seePodcast("field-notes")
      }
      robot<PlayerRobot> { seeCommands(toggles = 0, seeks = emptyList(), speeds = emptyList()) }

      composeRobot<AppShellRobot> {
        selectTab(Tab.LIBRARY)
        selectTab(Tab.HOME)
      }

      composeRobot<DiscoveryRobot> {
        discover()
        seeQuery("")
        seePodcast("quiet-current")
      }
      composeRobot<AppShellRobot> { selectTab(Tab.HOME) }
    }
  }

  @Test
  fun `failed and empty searches recover through retry and clear`() {
    uiTestRule.runPhoneRobotTest(darkTheme = false) {
      composeRobot<DiscoveryRobot> {
        discover(failRequest = true)
        retry()
        seePodcast("quiet-current")
        search("No matching podcast")
        seeEmptyResults()
        clearSearch()
        seeQuery("")
        seePodcast("quiet-current")
        backFromCatalog()
      }
      composeRobot<AppShellRobot> { seeSelectedTab(Tab.HOME) }
    }
  }

  @Test
  fun `catalog and show headers align and controls remain reachable across sizes themes and large text`() {
    listOf(
        Scenario(DpSize(412.dp, 916.dp)),
        Scenario(DpSize(800.dp, 1280.dp), darkTheme = true),
        Scenario(DpSize(1280.dp, 800.dp)),
        Scenario(DpSize(1600.dp, 900.dp), darkTheme = true),
        Scenario(DpSize(900.dp, 360.dp)),
        Scenario(DpSize(599.dp, 480.dp)),
        Scenario(DpSize(600.dp, 480.dp)),
        Scenario(DpSize(480.dp, 1000.dp), fontScale = 2f),
      )
      .forEach { scenario ->
        uiTestRule.runRobotTest(
          windowSize = scenario.size,
          darkTheme = scenario.darkTheme,
          fontScale = scenario.fontScale,
        ) {
          val homeProfileSize = profileSize()
          composeRobot<DiscoveryRobot> {
            discover()
            seePodcast("quiet-current")
          }
          onNodeWithTag("discovery-list").performScrollToIndex(0)
          val catalogHeader = headerBounds("discovery-back")
          assertThat(profileSize()).isEqualTo(homeProfileSize)
          onNodeWithTag("discovery-back").assertIsDisplayed()
          onNodeWithTag("discovery-search").assertIsDisplayed()
          saveScreenshot("catalog-${scenario.label()}")
          composeRobot<DiscoveryRobot> { openPodcast("quiet-current") }
          composeRobot<ShowRobot> { seeShow() }
          onNodeWithTag("show-back").assertIsDisplayed()
          assertThat(headerBounds("show-back")).isEqualTo(catalogHeader)
          assertThat(profileSize()).isEqualTo(homeProfileSize)
          saveScreenshot("show-${scenario.label()}")
          composeRobot<ShowRobot> { pressPreviewActions() }
          assertThat(headerBounds("show-back")).isEqualTo(catalogHeader)
          composeRobot<ShowRobot> { back() }
          onNodeWithTag("discovery-list").performScrollToIndex(0)
          assertThat(headerBounds("discovery-back")).isEqualTo(catalogHeader)
          composeRobot<AppShellRobot> { selectTab(Tab.HOME) }
        }
      }
  }

  @Test
  fun `selected show survives resizing between list detail and phone layouts`() {
    val size = mutableStateOf(DpSize(1600.dp, 900.dp))
    uiTestRule.runRobotTest(
      windowSize = DpSize(1600.dp, 1280.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      composeRobot<ShowRobot> { seeShow() }
      composeRobot<DiscoveryRobot> { openPodcast("field-notes") }
      val list = onNodeWithTag("show-list").assertIsDisplayed().getUnclippedBoundsInRoot()
      val detail = onNodeWithTag("show-detail").assertIsDisplayed().getUnclippedBoundsInRoot()
      assertThat(list.right).isLessThanOrEqualTo(detail.left)

      listOf(
          DpSize(412.dp, 916.dp),
          DpSize(800.dp, 1280.dp),
          DpSize(1280.dp, 800.dp),
          DpSize(599.dp, 480.dp),
          DpSize(600.dp, 480.dp),
          DpSize(1600.dp, 900.dp),
        )
        .forEach { next ->
          runOnIdle { size.value = next }
          waitForIdle()
          onNodeWithTag("show-detail").performScrollToNode(hasTestTag("show-title"))
          onNodeWithTag("show-title", useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals("Field Notes")
          if (next.width < 800.dp) {
            onNodeWithTag("show-list").assertDoesNotExist()
          }
          saveScreenshot("resized-${next.width.value.toInt()}x${next.height.value.toInt()}")
        }
      composeRobot<ShowRobot> { back() }

      composeRobot<DiscoveryRobot> { seePodcast("quiet-current") }
    }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/discovery-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }

  private fun ComposeUiTest.headerBounds(backTag: String): List<DpRect> = runOnUiThread {
    listOf("title-bar", "title-bar-title", backTag, "tab-profile").map {
      onNodeWithTag(it, useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
    }
  }

  private fun ComposeUiTest.profileSize(): DpSize = runOnUiThread {
    val bounds =
      onNodeWithContentDescription("Profile", useUnmergedTree = true)
        .assertIsDisplayed()
        .getUnclippedBoundsInRoot()
    DpSize(bounds.right - bounds.left, bounds.bottom - bounds.top)
  }

  private data class Scenario(
    val size: DpSize,
    val darkTheme: Boolean = false,
    val fontScale: Float = 1f,
  ) {
    fun label(): String =
      "${size.width.value.toInt()}x${size.height.value.toInt()}-${if (darkTheme) "dark" else "light"}-$fontScale"
  }
}
