package software.ralf.storymile.blackbox

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.ResultsReporter
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description

class FailureArtifactsRule : TestWatcher() {
  @Suppress(
    "TooGenericExceptionCaught",
  ) // Keep artifact failures from replacing the original test failure.
  override fun failed(error: Throwable, description: Description) {
    try {
      val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
      val reporter = ResultsReporter(description.methodName)
      val screenshot = reporter.addNewFile("${description.methodName}.png", "Screenshot")
      check(device.takeScreenshot(screenshot)) { "Could not capture the failure screenshot" }
      device.dumpWindowHierarchy(
        reporter.addNewFile("${description.methodName}.xml", "UI hierarchy"),
      )
      reporter
        .addNewFile("${description.methodName}-logcat.txt", "Logcat")
        .writeText(device.executeShellCommand("logcat -d -t 500"))
      reporter.reportToInstrumentation()
    } catch (exception: Throwable) {
      error.addSuppressed(exception)
    }
  }
}
