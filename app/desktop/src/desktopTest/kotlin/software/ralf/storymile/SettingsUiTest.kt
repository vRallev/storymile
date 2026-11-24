package software.ralf.storymile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThanOrEqualTo
import kotlin.test.Test
import software.ralf.app.platform.robot.composeRobot
import software.ralf.storymile.discovery.DiscoveryRobot
import software.ralf.storymile.tabs.TabsPresenter.Tab
import software.ralf.storymile.theme.StorymileColorSchemes

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
  fun `theme choices change app colors and System follows the host appearance`() {
    val systemDarkTheme = mutableStateOf<Boolean?>(false)
    DesktopUiTestRule().runRobotTest(
      windowSize = DesktopWindowSizes.phone,
      darkTheme = systemDarkTheme,
    ) {
      val settings = hasTestTag("settings-screen")
      val segments = hasAnyAncestor(hasTestTag("settings-theme-segments"))

      fun assertBackground(color: Color) {
        val pixels = onNode(settings).captureToImage().toPixelMap()
        // Sample the side gutter beyond the Settings cards.
        assertThat(pixels[1, pixels.height / 2]).isEqualTo(color)
      }

      fun chooseTheme(current: String, choice: String) {
        waitUntilAtLeastOneExists(matcher = segments and hasText(current), timeoutMillis = 5_000)
        onNode(segments and hasText(current)).assertIsSelected()
        val option = segments and hasText(choice)
        runOnUiThread { onNode(option).performScrollTo().performClick() }
        waitUntilAtLeastOneExists(matcher = option and isSelected(), timeoutMillis = 5_000)
      }

      runOnUiThread { onNodeWithTag("tab-profile").performClick() }
      waitUntilAtLeastOneExists(matcher = settings, timeoutMillis = 5_000)
      assertBackground(StorymileColorSchemes.paperTide.background)

      chooseTheme(current = "System", choice = "Dark")
      assertBackground(StorymileColorSchemes.harborNight.background)

      runOnIdle { systemDarkTheme.value = true }
      chooseTheme(current = "Dark", choice = "Light")
      assertBackground(StorymileColorSchemes.paperTide.background)

      runOnUiThread {
        onNode(hasContentDescription("Back") and hasAnyAncestor(settings)).performClick()
      }
      onNode(settings).assertDoesNotExist()
      runOnUiThread { onNodeWithTag("tab-profile").performClick() }
      chooseTheme(current = "Light", choice = "System")
      assertBackground(StorymileColorSchemes.harborNight.background)

      runOnIdle { systemDarkTheme.value = false }
      assertBackground(StorymileColorSchemes.paperTide.background)
    }
  }

  @Test
  fun `theme choices adapt to the available width and keep the selection`() {
    val wide = DpSize(width = 1000.dp, height = 480.dp)
    val size = mutableStateOf(DpSize(width = 320.dp, height = 1000.dp))
    DesktopUiTestRule().runRobotTest(
      windowSize = DpSize(width = 1000.dp, height = 1000.dp),
      contentSize = size,
      fontScale = 2f,
    ) {
      runOnUiThread { onNodeWithTag("tab-profile").performClick() }
      val rows = hasTestTag("settings-theme-radio-rows")
      waitUntilAtLeastOneExists(matcher = rows, timeoutMillis = 5_000)
      runOnUiThread { onNode(rows).performScrollTo() }
      onNodeWithTag("settings-theme-segments").assertDoesNotExist()

      val radioButtons =
        hasAnyAncestor(rows) and
          SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
      val system = onNode(radioButtons and hasText("System")).assertIsSelected().assertIsDisplayed()
      val light = onNode(radioButtons and hasText("Light")).assertIsDisplayed()
      val dark = radioButtons and hasText("Dark")
      val darkOption = onNode(dark).assertIsDisplayed()
      assertThat(system.getUnclippedBoundsInRoot().bottom)
        .isLessThanOrEqualTo(light.getUnclippedBoundsInRoot().top)
      assertThat(light.getUnclippedBoundsInRoot().bottom)
        .isLessThanOrEqualTo(darkOption.getUnclippedBoundsInRoot().top)

      runOnUiThread { darkOption.performClick() }
      waitUntilAtLeastOneExists(matcher = dark and isSelected(), timeoutMillis = 5_000)
      system.assertIsNotSelected()

      runOnIdle { size.value = wide }
      val segments = hasTestTag("settings-theme-segments")
      val darkSegment = hasAnyAncestor(segments) and hasText("Dark") and isSelected()
      waitUntilAtLeastOneExists(matcher = darkSegment, timeoutMillis = 5_000)
      runOnUiThread { onNode(darkSegment).performScrollTo() }
      onNode(darkSegment).assertIsDisplayed()
      onNode(rows).assertDoesNotExist()
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
        )) {
        val item = onNode(hasText(category) and inSections)
        runOnUiThread { item.performScrollTo().performClick() }
        item.assertIsSelected()
        onNode(hasText(category) and inDetail).assertIsDisplayed()
        onNode(hasText(setting) and inDetail).assertIsDisplayed()
      }

      val appearance = onNode(hasText("Appearance") and inSections)
      val themeTitle = onNode(hasText("Theme") and inDetail)
      runOnUiThread { appearance.performScrollTo().performClick() }
      appearance.assertIsSelected()
      themeTitle
        .assertIsDisplayed()
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
      onNode(hasText("Appearance") and inDetail).assertDoesNotExist()

      runOnIdle { size.value = DesktopWindowSizes.phone }
      onNodeWithTag("settings-overview").assertIsDisplayed()
      detail.assertDoesNotExist()
      runOnUiThread { onNodeWithText("Theme").performScrollTo() }
      onNodeWithText("Theme").assertIsDisplayed()

      runOnIdle { size.value = DesktopWindowSizes.tablet }
      appearance.assertIsSelected()
      themeTitle.assertIsDisplayed()

      composeRobot<AppShellRobot> { selectTab(Tab.DOWNLOADS) }
      onNodeWithTag("settings-screen").assertDoesNotExist()
      onNodeWithTag("playback").assertIsDisplayed()
    }
  }
}
