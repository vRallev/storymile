package software.ralf.storymile.blackbox

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.uiAutomator
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorymileReleaseTest {
  @get:Rule val failureArtifacts = FailureArtifactsRule()

  @Test
  fun showsAppNavigationAndEmptyLibraryAfterClearingAppData() = uiAutomator {
    assertThat(device.executeShellCommand("pm clear $APP_PACKAGE").trim()).isEqualTo("Success")
    device.pressHome()
    startApp(
      packageName = APP_PACKAGE,
      intentFlags = listOf(Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )

    listOf("tab-content", "tab-title", "home-search", "tabs").forEach { tag ->
      findVisibleTag(tag)
    }
    assertThat(device.hasObject(By.res("playback"))).isFalse()

    findVisibleTag("tab-library").click()
    waitForVisibleText("Your queue is empty")
    findVisibleTag("library-tab-shows").click()
    waitForVisibleText("No followed shows")
    findVisibleTag("library-tab-saved").click()
    waitForVisibleText("No saved episodes")
  }

  private fun UiAutomatorTestScope.findVisibleTag(tag: String) = onElement {
    packageName?.toString() == APP_PACKAGE && isVisibleToUser && viewIdResourceName == tag
  }

  private fun UiAutomatorTestScope.waitForVisibleText(expected: String) {
    onElement {
      packageName?.toString() == APP_PACKAGE && isVisibleToUser && text?.toString() == expected
    }
  }

  private companion object {
    const val APP_PACKAGE = "software.ralf.storymile"
  }
}
