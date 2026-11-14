package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isLessThan
import assertk.assertions.isStrictlyBetween
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.discovery.ShowRobot
import software.ralf.storymile.tabs.TabsPresenter

class LibraryUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `followed shows open shared details and unfollowing removes the library row`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DesktopWindowSizes.desktop,
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<DiscoveryRobot> {
        discover()
        openPodcast("quiet-current")
      }
      composeRobot<ShowRobot> {
        follow()
        back()
      }
      composeRobot<DiscoveryRobot> { openPodcast("field-notes") }
      composeRobot<ShowRobot> { follow() }
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      onNodeWithText("Followed shows").assertIsDisplayed()
      val first = "library-show-https://feeds.test/quiet-current"
      val second = "library-show-https://feeds.test/field-notes"

      openAndCheckArtworkTransition(
        row = first,
        artwork = "library-show-artwork-https://feeds.test/quiet-current",
      )
      composeRobot<ShowRobot> { seeShow() }
      onNodeWithTag("show-title").assertTextEquals("The Quiet Current")
      onNodeWithTag(first).assertDoesNotExist()
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
        onNodeWithTag("episode-0").performClick()
      }
      onNodeWithTag("episode-details-screen").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      composeRobot<ShowRobot> { seeFollowing(true) }

      runOnIdle { size.value = DesktopWindowSizes.desktop }
      onNodeWithTag(first).assertIsDisplayed().assertIsSelected()
      val list = onNodeWithTag("library-followed-shows").getUnclippedBoundsInRoot()
      val pane = onNodeWithTag("library-show-pane").getUnclippedBoundsInRoot()
      assertThat(list.right).isLessThan(pane.left)
      openAndCheckArtworkTransition(
        row = second,
        artwork = "library-show-artwork-https://feeds.test/field-notes",
      )
      onNodeWithTag(second).assertIsDisplayed().assertIsSelected()
      onNodeWithTag("show-title").assertTextEquals("Field Notes")
      saveScreenshot("desktop-followed-shows")
      composeRobot<ShowRobot> { unfollow() }
      onNodeWithTag(second).assertDoesNotExist()
      runOnUiThread { onNodeWithTag("show-back").performClick() }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      onNodeWithTag(first).assertIsDisplayed()
    }
  }

  @Test
  fun `library renders in the production graph on phone tablet and desktop in both themes`() {
    listOf(
        "phone" to DesktopWindowSizes.phone,
        "tablet" to DesktopWindowSizes.tablet,
        "desktop" to DesktopWindowSizes.desktop,
      )
      .forEach { (device, size) ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
            onNodeWithTag("tab-library").assertIsSelected()
            onNodeWithTag("library-empty").assertIsDisplayed()
            onNodeWithTag("library-illustration", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-empty-message", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("library-discover").assertIsDisplayed()
            onNodeWithTag("library-tab-queue").assertIsSelected()
            onNodeWithTag("library-tab-shows").assertIsNotSelected()
            onNodeWithTag("library-tab-saved").assertIsNotSelected()
            onNodeWithTag("library-tab-history").assertIsNotSelected()
            listOf(
                "queue" to "Your queue is empty",
                "shows" to "No followed shows",
                "saved" to "No saved episodes",
                "history" to "No listening history yet",
              )
              .forEach { (tab, title) ->
                runOnUiThread { onNodeWithTag("library-tab-$tab").performClick() }
                onNodeWithTag("library-tab-$tab").assertIsSelected()
                onNodeWithText(title).assertIsDisplayed()
                saveScreenshot("$device-${if (darkTheme) "dark" else "light"}-$tab")
              }
          }
        }
      }
  }

  @Test
  fun `swiped library tab survives resizing`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DpSize(1440.dp, 1000.dp),
      darkTheme = false,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-saved").assertIsSelected()
      runOnUiThread { onNodeWithTag("library-pager").performTouchInput { swipeLeft() } }
      onNodeWithTag("library-tab-history").assertIsSelected()

      listOf(
          DesktopWindowSizes.tablet,
          DesktopWindowSizes.desktop,
          DpSize(900.dp, 360.dp),
          DpSize(320.dp, 1000.dp),
          DesktopWindowSizes.phone,
        )
        .forEach { next ->
          runOnIdle { size.value = next }
          onNodeWithTag("tab-library").assertIsSelected()
          onNodeWithTag("library-tab-history").assertIsSelected()
          onNodeWithTag("library-tab-saved").assertIsNotSelected()
          onNodeWithTag("library-tab-shows").assertIsNotSelected()
          onNodeWithTag("library-tab-queue").assertIsNotSelected()
          onNodeWithTag("library-discover").assertIsDisplayed()
          saveScreenshot("resized-${next.width.value.toInt()}-${next.height.value.toInt()}")
        }
    }
  }

  @Test
  fun `every empty library tab opens discovery and back returns to the selected tab`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DesktopWindowSizes.desktop)
      .forEach { size ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = false) {
          composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
          listOf("queue", "shows", "saved", "history").forEach { tab ->
            runOnUiThread { onNodeWithTag("library-tab-$tab").performClick() }
            onNodeWithTag("library-tab-$tab").assertIsSelected()
            runOnUiThread { onNodeWithTag("library-discover").performClick() }

            composeRobot<DiscoveryRobot> {
              seePodcast("quiet-current")
              openPodcast("quiet-current")
            }
            onNodeWithTag("tab-library").assertIsSelected()
            composeRobot<ShowRobot> {
              seeShow()
              back()
            }
            composeRobot<DiscoveryRobot> { backFromCatalog() }
            onNodeWithTag("library-empty").assertIsDisplayed()
            onNodeWithTag("library-tab-$tab").assertIsSelected()
            onNodeWithTag("tab-library").assertIsSelected()
          }
          composeRobot<AppShellRobot> {
            selectTab(TabsPresenter.Tab.HOME)
            selectTab(TabsPresenter.Tab.LIBRARY)
          }
          onNodeWithTag("library-tab-history").assertIsSelected()
        }
      }
  }

  private fun ComposeUiTest.openAndCheckArtworkTransition(row: String, artwork: String) {
    val origin = artworkBounds(artwork)
    mainClock.autoAdvance = false
    try {
      runOnUiThread { onNodeWithTag(row).performClick() }
      waitUntil(timeoutMillis = 5_000) {
        mainClock.advanceTimeByFrame()
        onAllNodes(hasTestTag("show-artwork"), useUnmergedTree = true)
          .fetchSemanticsNodes()
          .isNotEmpty()
      }
      mainClock.advanceTimeBy(112)
      val frame = artworkBounds("show-artwork")
      mainClock.advanceTimeBy(800)
      val target = artworkBounds("show-artwork")
      val distance = (target.center - origin.center).getDistance()
      assertThat((frame.center - origin.center).getDistance()).isStrictlyBetween(1f, distance - 1f)
      assertThat(frame.width).isStrictlyBetween(origin.width, target.width)
    } finally {
      mainClock.autoAdvance = true
    }
  }

  private fun ComposeUiTest.artworkBounds(tag: String): Rect = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("library-empty").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/library-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
