package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import assertk.assertThat
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter.Tab

class SettingsUiTest {
  @Test
  fun `profile opens guest settings and back restores Discovery search`() {
    DesktopUiTestRule().runPhoneRobotTest {
      composeRobot<DiscoveryRobot> {
        discover()
        search("Field")
      }

      runOnUiThread { onNodeWithTag("tab-profile").performClick() }
      val settings = hasTestTag("settings-screen")
      waitUntilAtLeastOneExists(matcher = settings, timeoutMillis = 5_000)
      onNodeWithTag("settings-overview").assertIsDisplayed()
      onNodeWithTag("settings-detail").assertDoesNotExist()
      onNodeWithTag("discovery-screen").assertDoesNotExist()

      val signIn = onNode(hasText("Sign in to sync") and hasClickAction()).assertIsDisplayed()
      val skipSilence = onNodeWithTag("settings-skip-silence").assertIsOn()
      runOnUiThread {
        signIn.performClick()
        skipSilence.performClick()
      }
      signIn.assertIsDisplayed()
      skipSilence.assertIsOn()

      runOnUiThread {
        onNode(hasContentDescription("Back") and hasAnyAncestor(settings)).performClick()
      }
      composeRobot<DiscoveryRobot> { seeQuery("Field") }
      onNode(settings).assertDoesNotExist()
    }
  }

  @Test
  fun `wide settings switches sections and restores the selection after resizing`() {
    val size = mutableStateOf(DesktopWindowSizes.desktop)
    DesktopUiTestRule(withEpisodes = true).runRobotTest(
      windowSize = DesktopWindowSizes.desktop,
      contentSize = size,
      darkTheme = true,
    ) {
      composeRobot<AppShellRobot> { openPlayback() }
      val expandedPlayer = hasTestTag("playback-screen")
      runOnUiThread {
        onNode(hasContentDescription("Profile") and hasAnyAncestor(expandedPlayer)).performClick()
      }
      waitUntilAtLeastOneExists(matcher = hasTestTag("settings-screen"), timeoutMillis = 5_000)
      onNode(expandedPlayer).assertDoesNotExist()
      onNodeWithTag("playback").assertIsDisplayed()

      val sections = onNodeWithTag("settings-sections")
      val detail = onNodeWithTag("settings-detail")
      for (wideSize in listOf(DesktopWindowSizes.desktop, DesktopWindowSizes.tablet)) {
        runOnIdle { size.value = wideSize }
        assertThat(sections.assertIsDisplayed().getUnclippedBoundsInRoot().right)
          .isLessThanOrEqualTo(detail.assertIsDisplayed().getUnclippedBoundsInRoot().left)
      }

      val inSections = hasAnyAncestor(hasTestTag("settings-sections"))
      val inDetail = hasAnyAncestor(hasTestTag("settings-detail"))
      onNode(hasText("Playback") and inSections).assertIsSelected()
      onNode(hasText("Rewind on resume") and inDetail).assertIsDisplayed()
      for ((category, setting) in
        listOf(
          "Downloads" to "Wi-Fi only",
          "Notifications" to "No settings available yet.",
          "Privacy" to "No settings available yet.",
          "Appearance" to "Theme",
        )) {
        val item = onNode(hasText(category) and inSections)
        runOnUiThread { item.performScrollTo().performClick() }
        item.assertIsSelected()
        onNode(hasText(category) and inDetail).assertIsDisplayed()
        onNode(hasText(setting) and inDetail).assertIsDisplayed()
      }

      runOnIdle { size.value = DesktopWindowSizes.phone }
      onNodeWithTag("settings-overview").assertIsDisplayed()
      detail.assertDoesNotExist()
      runOnUiThread { onNodeWithText("Theme").performScrollTo() }
      onNodeWithText("Theme").assertIsDisplayed()

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      onNode(hasText("Appearance") and inSections).assertIsSelected()
      onNode(hasText("Theme") and inDetail).assertIsDisplayed()

      composeRobot<AppShellRobot> { selectTab(Tab.DOWNLOADS) }
      onNodeWithTag("settings-screen").assertDoesNotExist()
      onNodeWithTag("playback").assertIsDisplayed()
    }
  }
}
