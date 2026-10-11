package software.ralf.storymile.blackbox

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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
  fun showsAppNavigationAndContent() = uiAutomator {
    assertThat(device.executeShellCommand("pm clear $APP_PACKAGE").trim()).isEqualTo("Success")
    device.pressHome()
    val context = InstrumentationRegistry.getInstrumentation().context
    val intent = requireNotNull(context.packageManager.getLaunchIntentForPackage(APP_PACKAGE))
    context.startActivity(
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
    )

    listOf("tab-content", "tabs").forEach { tag ->
      onElement(timeoutMs = 10_000) {
        packageName?.toString() == APP_PACKAGE && isVisibleToUser && viewIdResourceName == tag
      }
    }
    assertThat(device.hasObject(By.res("playback"))).isFalse()
  }

  private companion object {
    const val APP_PACKAGE = "software.ralf.storymile"
  }
}
