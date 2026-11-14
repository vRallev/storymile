package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isTrue
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
  fun `saved shows open in Library and adapt while keeping the selected destination`() {
    val size = mutableStateOf(DesktopWindowSizes.phone)
    uiTestRule.runRobotTest(
      windowSize = DesktopWindowSizes.desktop,
      darkTheme = true,
      contentSize = size,
    ) {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      waitUntilAtLeastOneExists(matcher = hasText("No followed shows"), timeoutMillis = 5_000)
      runOnUiThread { onNodeWithTag("library-discover").performClick() }
      listOf("quiet-current", "field-notes").forEach { podcast ->
        composeRobot<DiscoveryRobot> { openPodcast(podcast) }
        composeRobot<ShowRobot> {
          follow()
          back()
        }
      }
      composeRobot<DiscoveryRobot> { backFromCatalog() }

      val quietCurrent = onNodeWithTag("library-show-https://feeds.test/quiet-current")
      val fieldNotes = onNodeWithTag("library-show-https://feeds.test/field-notes")
      waitUntilAtLeastOneExists(matcher = hasText("Followed shows"), timeoutMillis = 5_000)
      saveScreenshot("followed-phone-dark")
      runOnUiThread { quietCurrent.performClick() }
      onNodeWithTag("title-bar-title").assertTextEquals("Library")
      onNodeWithTag("library-tab-shows").assertDoesNotExist()
      onNodeWithTag("show-title").assertTextEquals("The Quiet Current")
      composeRobot<ShowRobot> { seeFollowing(true) }
      saveScreenshot("show-phone-dark")

      runOnIdle { size.value = DpSize(320.dp, 840.dp) }
      onNodeWithTag("show-detail").assertIsDisplayed()
      onNodeWithTag("show-list").assertDoesNotExist()

      listOf(DpSize(900.dp, 360.dp), DesktopWindowSizes.tablet, DesktopWindowSizes.desktop)
        .forEach { next ->
          runOnIdle { size.value = next }
          onNodeWithTag("library-tab-shows").assertIsSelected()
          onNodeWithTag("show-list").assertIsDisplayed()
          quietCurrent.assertIsSelected()
          assertThat(bounds("show-list").right).isLessThan(bounds("show-detail").left)
          assertThat(bounds("show-list").top).isGreaterThan(bounds("library-tab-shows").top)
        }
      saveScreenshot("show-desktop-dark")

      runOnUiThread { fieldNotes.performClick() }
      fieldNotes.assertIsSelected()
      onNodeWithTag("show-title").assertTextEquals("Field Notes")
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-0"))
      }
      val showPaneBefore = bounds("show-detail")
      mainClock.autoAdvance = false
      val panePositions =
        try {
          runOnUiThread { onNodeWithTag("episode-0").performClick() }
          List(24) {
            mainClock.advanceTimeByFrame()
            bounds("show-detail").left
          }
        } finally {
          mainClock.autoAdvance = true
        }
      onNodeWithTag("episode-details-title").assertTextEquals("Field Notes episode 1")
      onNodeWithTag("library-tab-shows").assertIsSelected()
      onNodeWithTag("show-list").assertDoesNotExist()
      val showPaneAfter = bounds("show-detail")
      assertThat(panePositions.any { it > showPaneAfter.left && it < showPaneBefore.left }).isTrue()
      assertThat(bounds("show-detail").right).isLessThan(bounds("episode-details-screen").left)
      saveScreenshot("show-episode-desktop-dark")

      runOnIdle { size.value = DesktopWindowSizes.phone }
      onNodeWithTag("episode-details-title").assertTextEquals("Field Notes episode 1")
      onNodeWithTag("show-list").assertDoesNotExist()
      runOnUiThread { onNodeWithTag("episode-details-back").performClick() }
      onNodeWithTag("show-title").assertTextEquals("Field Notes")
      composeRobot<ShowRobot> { unfollow() }
      runOnUiThread { onNodeWithTag("show-back").performClick() }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      quietCurrent.assertIsDisplayed()
      fieldNotes.assertDoesNotExist()
    }
  }

  private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()

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
            onNodeWithTag("library-screen").assertIsDisplayed()
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
            onNodeWithTag("library-screen").assertIsDisplayed()
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

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("library-screen").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/library-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
