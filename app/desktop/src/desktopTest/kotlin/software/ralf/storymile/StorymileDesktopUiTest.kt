@file:OptIn(ExperimentalTestApi::class)

package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import software.ralf.app.platform.robot.composeRobot
import software.ralf.app.platform.robot.waitUntilCatching
import software.ralf.storymile.tabs.TabsPresenter

class StorymileDesktopUiTest {
  private val uiTestRule = DesktopUiTestRule()

  @Test
  fun `logo follows app theme changes without losing the selected tab`() {
    val darkTheme = mutableStateOf<Boolean?>(false)
    uiTestRule.runRobotTest(windowSize = DesktopWindowSizes.tablet, darkTheme = darkTheme) {
      composeRobot<AppShellRobot> {
        seeBranding(visible = true)
        selectTab(TabsPresenter.Tab.DOWNLOADS)
      }
      val logoColor = {
        val pixels = onNodeWithTag("storymile-logo").captureToImage().toPixelMap()
        // The center is opaque, so changes in the surface color cannot affect this pixel.
        pixels[pixels.width / 2, pixels.height / 2]
      }
      val lightLogo = logoColor()

      runOnIdle { darkTheme.value = true }
      assertThat(logoColor()).isNotEqualTo(lightLogo)
      composeRobot<AppShellRobot> { seeSelectedTab(TabsPresenter.Tab.DOWNLOADS) }

      runOnIdle { darkTheme.value = false }
      assertThat(logoColor()).isEqualTo(lightLogo)
      composeRobot<AppShellRobot> { seeSelectedTab(TabsPresenter.Tab.DOWNLOADS) }
    }
  }

  @Test
  fun `tabs select content in bottom rail and expanded navigation`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          composeRobot<AppShellRobot> {
            seeAppLayers()
            seeBranding(visible = size != DesktopWindowSizes.phone)
            listOf(TabsPresenter.Tab.DOWNLOADS, TabsPresenter.Tab.LIBRARY, TabsPresenter.Tab.HOME)
              .forEach { tab ->
                selectTab(tab)
                seeSelectedTab(tab)
              }
          }
        }
      }
    }
  }

  @Test
  fun `phone shows selected tab content playback and bottom tabs`() {
    listOf(false, true).forEach { darkTheme ->
      uiTestRule.runPhoneRobotTest(darkTheme = darkTheme) {
        waitUntilCatching("compact app containers displayed", timeout = 3.seconds) {
          composeRobot<AppShellRobot> { seeCompactLayout() }
        }
        saveScreenshot("phone-${if (darkTheme) "dark" else "light"}")
      }
    }
  }

  @Test
  fun `tablet shows selected tab content side tabs and full width playback`() {
    listOf(false, true).forEach { darkTheme ->
      uiTestRule.runTabletRobotTest(darkTheme = darkTheme) {
        waitUntilCatching("expanded app containers displayed", timeout = 3.seconds) {
          composeRobot<AppShellRobot> { seeExpandedLayout() }
        }
        saveScreenshot("tablet-${if (darkTheme) "dark" else "light"}")
      }
    }
  }

  @Test
  fun `sidebar expansion follows width instead of height`() {
    listOf(
        DpSize(1440.dp, 900.dp) to 360.dp,
        DpSize(1199.dp, 900.dp) to 80.dp,
        DpSize(1200.dp, 900.dp) to 360.dp,
        DpSize(1200.dp, 500.dp) to 360.dp,
        DpSize(900.dp, 1440.dp) to 80.dp,
      )
      .forEach { (size, tabWidth) ->
        listOf(false, true).forEach { darkTheme ->
          uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
            waitUntilCatching("side tabs displayed at the expected width", timeout = 3.seconds) {
              composeRobot<AppShellRobot> { seeExpandedLayout(tabWidth = tabWidth) }
            }
            val dimensions = "${size.width.value.toInt()}x${size.height.value.toInt()}"
            saveScreenshot("sidebar-$dimensions-${if (darkTheme) "dark" else "light"}")
          }
        }
      }
  }

  @Test
  fun `dragging playback expands to the window and collapses back to the bar`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet, DpSize(1440.dp, 900.dp)).forEach {
      size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          val phone = size == DesktopWindowSizes.phone
          composeRobot<AppShellRobot> {
            seeAppLayers()
            dragPlaybackUp()
          }
          waitUntilCatching("playback screen expanded", timeout = 3.seconds) {
            composeRobot<AppShellRobot> { seePlaybackScreen(phone) }
          }
          val dimensions = "${size.width.value.toInt()}x${size.height.value.toInt()}"
          saveScreenshot("playback-$dimensions-${if (darkTheme) "dark" else "light"}")
          composeRobot<AppShellRobot> { dragPlaybackDown() }
          waitUntilCatching("playback bar restored", timeout = 3.seconds) {
            composeRobot<AppShellRobot> {
              if (phone) seeCompactLayout()
              else seeExpandedLayout(if (size.width >= 1200.dp) 360.dp else 80.dp)
            }
          }
        }
      }
    }
  }

  @Test
  fun `playback opens by click and Escape returns to the phone bar`() {
    uiTestRule.runPhoneRobotTest {
      composeRobot<AppShellRobot> { openPlayback() }
      waitUntilCatching("playback screen expanded", timeout = 3.seconds) {
        composeRobot<AppShellRobot> { seePlaybackScreen(phone = true) }
      }
      onNodeWithTag("playback-screen").performKeyInput {
        keyDown(Key.Escape)
        keyUp(Key.Escape)
      }
      waitUntilCatching("playback bar restored", timeout = 3.seconds) {
        composeRobot<AppShellRobot> { seeCompactLayout() }
      }
    }
  }

  @Test
  fun `playback corners flatten during drag and fill the expanded screen`() {
    listOf(DesktopWindowSizes.phone, DpSize(1440.dp, 900.dp)).forEach { size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          val phone = size == DesktopWindowSizes.phone
          val originalTop = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
          val originalCorners = playbackCornerGaps()
          originalCorners.forEach { assertThat(it).isGreaterThan(1) }
          mainClock.autoAdvance = false
          try {
            onNodeWithTag("playback").performTouchInput { down(Offset(center.x, 32f)) }
            mainClock.advanceTimeBy(1000)
            onRoot().performTouchInput { moveBy(Offset(0f, -originalTop / 2)) }
            mainClock.advanceTimeByFrame()
            val halfExpandedCorners = playbackCornerGaps()
            halfExpandedCorners.zip(originalCorners).forEach { (current, original) ->
              assertThat(current).isGreaterThan(1)
              assertThat(current).isLessThan(original)
            }

            onRoot().performTouchInput { moveBy(Offset(0f, originalTop / 4)) }
            mainClock.advanceTimeByFrame()
            playbackCornerGaps().zip(halfExpandedCorners).forEach { (current, previous) ->
              assertThat(current).isGreaterThan(previous)
            }
            onRoot().performTouchInput { moveBy(Offset(0f, originalTop / 2)) }
          } finally {
            onRoot().performTouchInput {
              advanceEventTime(100)
              up()
            }
            mainClock.autoAdvance = true
          }
          waitUntilCatching("rounded playback bar restored", timeout = 3.seconds) {
            composeRobot<AppShellRobot> {
              if (phone) seeCompactLayout() else seeExpandedLayout(tabWidth = 360.dp)
            }
            assertThat(playbackCornerGaps()).isEqualTo(originalCorners)
          }

          composeRobot<AppShellRobot> { dragPlaybackUp() }
          waitUntilCatching("expanded playback covers both top corners", timeout = 3.seconds) {
            composeRobot<AppShellRobot> { seePlaybackScreen(phone) }
            playbackCornerGaps().forEach { assertTrue(it <= 1) }
          }
          composeRobot<AppShellRobot> { dragPlaybackDown() }
          waitUntilCatching("collapsed playback corners restored", timeout = 3.seconds) {
            assertThat(playbackCornerGaps()).isEqualTo(originalCorners)
          }
        }
      }
    }
  }

  @Test
  fun `phone tabs slide with playback while tablet tabs stay in place`() {
    listOf(DesktopWindowSizes.phone, DesktopWindowSizes.tablet).forEach { size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          val phone = size == DesktopWindowSizes.phone
          val originalTabs = onNodeWithTag("tabs").getUnclippedBoundsInRoot()
          val originalPlaybackTop = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
          mainClock.autoAdvance = false
          try {
            onNodeWithTag("playback").performTouchInput { down(Offset(center.x, 32f)) }
            onRoot().performTouchInput { moveBy(Offset(0f, -48f)) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("tabs").assertIsDisplayed()
            assertTabTravel(originalTabs, originalPlaybackTop, phone)
            saveScreenshot(
              "tabs-drag-${if (phone) "phone" else "tablet"}-${if (darkTheme) "dark" else "light"}"
            )

            onRoot().performTouchInput { moveBy(Offset(0f, 12f)) }
            mainClock.advanceTimeByFrame()
            assertTabTravel(originalTabs, originalPlaybackTop, phone)

            onRoot().performTouchInput { moveBy(Offset(0f, -100f)) }
            mainClock.advanceTimeByFrame()
            if (phone) onNodeWithTag("tabs").assertIsNotDisplayed()
            else onNodeWithTag("tabs").assertIsDisplayed()
            assertTabTravel(originalTabs, originalPlaybackTop, phone)

            onRoot().performTouchInput { moveBy(Offset(0f, 100f)) }
            mainClock.advanceTimeByFrame()
            onNodeWithTag("tabs").assertIsDisplayed()
            assertTabTravel(originalTabs, originalPlaybackTop, phone)
          } finally {
            onRoot().performTouchInput {
              advanceEventTime(100)
              up()
            }
            mainClock.autoAdvance = true
          }
          waitUntilCatching("playback bar and tabs restored", timeout = 3.seconds) {
            composeRobot<AppShellRobot> {
              if (phone) seeCompactLayout() else seeExpandedLayout()
            }
          }
        }
      }
    }
  }

  @Test
  fun `playback touch feedback covers the exposed sheet during a drag`() {
    listOf(DesktopWindowSizes.phone, DpSize(1440.dp, 900.dp)).forEach { size ->
      listOf(false, true).forEach { darkTheme ->
        uiTestRule.runRobotTest(windowSize = size, darkTheme = darkTheme) {
          val original = onNodeWithTag("playback").fetchSemanticsNode().boundsInRoot
          val x = original.center.x.roundToInt()
          val surface = onRoot().captureToImage().toPixelMap()[x, (original.top + 32).roundToInt()]
          mainClock.autoAdvance = false
          try {
            onNodeWithTag("playback").performTouchInput { down(Offset(center.x, 32f)) }
            mainClock.advanceTimeBy(300)
            onRoot().performTouchInput { moveBy(Offset(0f, -240f)) }
            mainClock.advanceTimeByFrame()
            val dragged = onNodeWithTag("playback-screen").fetchSemanticsNode().boundsInRoot
            assertThat(original.top - dragged.top).isGreaterThan(original.height)
            val feedback = onRoot().captureToImage().toPixelMap()
            assertThat(feedback[x, (dragged.top + original.height + 16).roundToInt()])
              .isNotEqualTo(surface)
            val dimensions = "${size.width.value.toInt()}x${size.height.value.toInt()}"
            saveScreenshot("drag-feedback-$dimensions-${if (darkTheme) "dark" else "light"}")
          } finally {
            onRoot().performTouchInput { up() }
            mainClock.autoAdvance = true
          }
        }
      }
    }
  }

  private fun ComposeUiTest.playbackCornerGaps(): List<Int> {
    val top = onNodeWithTag("playback-sheet").fetchSemanticsNode().boundsInRoot.top
    val pixels = onRoot().captureToImage().toPixelMap()
    val y = ceil(top).toInt() + 3
    val surface = pixels[pixels.width / 2, y]
    return listOf(
      (0 until pixels.width / 2).first { pixels[it, y] == surface },
      (0 until pixels.width / 2).first { pixels[pixels.width - 1 - it, y] == surface },
    )
  }

  private fun ComposeUiTest.assertTabTravel(
    originalTabs: DpRect,
    originalPlaybackTop: Dp,
    phone: Boolean,
  ) {
    val tabs = onNodeWithTag("tabs").getUnclippedBoundsInRoot()
    val playbackTop = onNodeWithTag("playback-sheet").getUnclippedBoundsInRoot().top
    val playbackTravel = originalPlaybackTop - playbackTop
    assertThat(playbackTravel).isGreaterThan(0.dp)
    if (phone) {
      val tabTravel = tabs.top - originalTabs.top
      assertTrue(abs((tabTravel - playbackTravel).value) <= 1f)
    } else {
      assertThat(tabs).isEqualTo(originalTabs)
    }
  }

  private fun ComposeUiTest.saveScreenshot(name: String) {
    val file = File("build/reports/screenshots/empty-shell-$name.png")
    file.parentFile.mkdirs()
    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
  }
}
