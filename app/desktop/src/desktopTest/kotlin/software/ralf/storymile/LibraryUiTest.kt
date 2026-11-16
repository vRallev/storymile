package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
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
      followShowsInLibrary()

      val quietCurrent = onNodeWithTag("library-show-https://feeds.test/quiet-current")
      val fieldNotes = onNodeWithTag("library-show-https://feeds.test/field-notes")
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

  @Test
  fun `tablet Shows selects the first followed show and restores an open episode`() {
    uiTestRule.runTabletRobotTest {
      followShowsInLibrary()

      val fieldNotes = onNodeWithTag("library-show-https://feeds.test/field-notes")
      val quietCurrent = onNodeWithTag("library-show-https://feeds.test/quiet-current")
      fieldNotes.assertIsSelected()
      quietCurrent.assertIsNotSelected()
      onNodeWithTag("show-back").assertDoesNotExist()
      onNodeWithTag("show-title").assertTextEquals("Field Notes")
      assertThat(bounds("show-list").right).isLessThan(bounds("show-detail").left)

      runOnUiThread { quietCurrent.performClick() }
      quietCurrent.assertIsSelected()
      runOnUiThread {
        onNodeWithTag("show-detail").performScrollToNode(hasTestTag("episode-1"))
        onNodeWithTag("episode-1").performClick()
      }
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 2")

      runOnUiThread { onNodeWithTag("library-tab-history").performClick() }
      onNodeWithTag("library-tab-history").assertIsSelected()
      onNodeWithText("No listening history yet").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("library-tab-shows").performClick() }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      onNodeWithTag("episode-details-title").assertTextEquals("The Quiet Current episode 2")
      assertThat(bounds("show-detail").right).isLessThan(bounds("episode-details-screen").left)

      onNodeWithTag("show-back").assertIsDisplayed()
      runOnUiThread { onNodeWithTag("show-back").performClick() }
      onNodeWithTag("episode-details-screen").assertDoesNotExist()
      onNodeWithTag("show-back").assertDoesNotExist()

      val edgeY = onRoot().fetchSemanticsNode().boundsInRoot.height / 2f
      runOnUiThread {
        onRoot().performMouseInput {
          moveTo(Offset(x = 2f, y = edgeY))
          press()
          moveTo(Offset(x = 225f, y = edgeY))
          release()
        }
      }
      onNodeWithTag("library-tab-shows").assertIsSelected()
      quietCurrent.assertIsSelected()
      onNodeWithTag("show-title").assertTextEquals("The Quiet Current")
    }
  }

  @Test
  fun `phone show transition keeps the tabs and list in place while the artwork moves`() {
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.phone) {
      followShowsInLibrary()
      val selected = "library-show-https://feeds.test/field-notes"
      val nextRow = "library-show-https://feeds.test/quiet-current"
      val tabs = "library-tab-shows"
      val listTop = bounds(nextRow).top
      val tabsTop = bounds(tabs).top
      val startArtworkWidth =
        transitionBounds("library-show-artwork-https://feeds.test/field-notes").width

      mainClock.autoAdvance = false
      try {
        runOnUiThread { onNodeWithTag(selected).performClick() }
        awaitEntry("show-artwork")
        mainClock.advanceTimeBy(96)
        assertThat(bounds(tabs).top, "outgoing tabs").isEqualTo(tabsTop)
        assertThat(bounds(nextRow).top, "outgoing row").isEqualTo(listTop)
        val artworkInMotion = transitionBounds("show-artwork").width
        mainClock.advanceTimeBy(1_000)
        val detailArtworkWidth = transitionBounds("show-artwork").width
        assertThat(artworkInMotion).isGreaterThan(startArtworkWidth)
        assertThat(artworkInMotion).isLessThan(detailArtworkWidth)
        onNodeWithTag(tabs).assertDoesNotExist()

        runOnUiThread { onNodeWithTag("show-back").performClick() }
        awaitEntry(tabs)
        mainClock.advanceTimeBy(96)
        assertThat(bounds(tabs).top, "returning tabs").isEqualTo(tabsTop)
        assertThat(bounds(nextRow).top, "returning row").isEqualTo(listTop)
        val artworkReturning =
          transitionBounds("library-show-artwork-https://feeds.test/field-notes").width
        assertThat(artworkReturning).isGreaterThan(startArtworkWidth)
        assertThat(artworkReturning).isLessThan(detailArtworkWidth)
        mainClock.advanceTimeBy(1_000)
        onNodeWithTag(tabs).assertIsSelected()
        assertThat(bounds(nextRow).top, "restored row").isEqualTo(listTop)
      } finally {
        mainClock.autoAdvance = true
      }
    }
  }

  private fun ComposeUiTest.followShowsInLibrary() {
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
    waitUntilAtLeastOneExists(matcher = hasText("Followed shows"), timeoutMillis = 5_000)
  }

  private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag).getUnclippedBoundsInRoot()

  private fun ComposeUiTest.awaitEntry(tag: String) {
    waitUntil(timeoutMillis = 5_000) {
      mainClock.advanceTimeByFrame()
      onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    mainClock.advanceTimeByFrame()
  }

  private fun ComposeUiTest.transitionBounds(tag: String) = runOnUiThread {
    val coordinates =
      onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().layoutInfo.coordinates
    coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
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
                if (tab == "shows") {
                  waitUntilAtLeastOneExists(matcher = hasText(title), timeoutMillis = 5_000)
                }
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

  @Test
  fun `tapping the Library tab returns from discovery to the selected library page`() {
    uiTestRule.runPhoneRobotTest {
      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      runOnUiThread { onNodeWithTag("library-tab-history").performClick() }
      onNodeWithTag("library-tab-history").assertIsSelected()
      runOnUiThread { onNodeWithTag("library-discover").performClick() }
      composeRobot<DiscoveryRobot> { openPodcast("quiet-current") }
      composeRobot<ShowRobot> { seeShow() }

      composeRobot<AppShellRobot> { selectTab(TabsPresenter.Tab.LIBRARY) }
      onNodeWithTag("library-tab-history").assertIsSelected()
      onNodeWithText("No listening history yet").assertIsDisplayed()
      onNodeWithTag("show-detail").assertDoesNotExist()
      onNodeWithTag("discovery-screen").assertDoesNotExist()
    }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    runOnUiThread { onNodeWithTag("library-screen").performMouseInput { moveTo(center) } }
    val file = File("build/reports/screenshots/library-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onNodeWithTag("app-shell").captureToImage().toAwtImage(), "png", file)
  }
}
