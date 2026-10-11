package software.ralf.storymile

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.Robot
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

/** Checks the runtime mode used by an integration-test application. */
@ContributesRobot(AppScope::class)
class RuntimeModeRobot(private val controller: RuntimeModeController) : Robot {
  /** Checks that the application uses fake mode and rejects a switch to real mode. */
  suspend fun seeLockedFakeMode() {
    assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
    assertFailure { controller.switchMode(RuntimeMode.Real) }.isInstanceOf<IllegalStateException>()
    assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
  }
}
