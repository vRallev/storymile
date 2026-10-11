package software.ralf.storymile

import assertk.assertThat
import assertk.assertions.isEqualTo
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import software.ralf.app.platform.scope.di.metro.metroDependencyGraph
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

class IntegrationRuntimeModeTest {
  @Test
  fun `test applications stay in fake mode across mode changes and recreation`() = runTest {
    repeat(2) {
      val application = KmpApplication()
      application.create(createGraphFactory<TestDesktopAppGraph.Factory>().create(application))
      try {
        val controller: RuntimeModeController =
          application.rootScope.metroDependencyGraph<TestDesktopAppGraph>().runtimeModeController
        assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)

        controller.switchMode(RuntimeMode.Real)
        assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)

        controller.switchMode(RuntimeMode.Fake)
        assertThat(controller.mode()).isEqualTo(RuntimeMode.Fake)
      } finally {
        application.destroy()
      }
    }
  }
}
