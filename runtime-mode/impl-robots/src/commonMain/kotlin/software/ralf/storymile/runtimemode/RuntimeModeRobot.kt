package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.Robot

/** Selects the data source for integration tests. */
@ContributesRobot(AppScope::class)
class RuntimeModeRobot(private val controller: RuntimeModeController) : Robot {
  /** Selects local fake services. */
  fun useFakeMode() {
    useRuntimeMode(RuntimeMode.Fake)
  }

  /** Selects real services. */
  fun useRealMode() {
    useRuntimeMode(RuntimeMode.Real)
  }

  /** Selects [mode] before the next service operation. */
  fun useRuntimeMode(mode: RuntimeMode) {
    controller.switchMode(mode)
  }
}
