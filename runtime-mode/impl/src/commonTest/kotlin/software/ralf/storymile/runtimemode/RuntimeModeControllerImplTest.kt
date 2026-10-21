package software.ralf.storymile.runtimemode

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.restart.ApplicationRestarter

class RuntimeModeControllerImplTest {
  @Test
  fun `a missing saved mode uses Real without restarting`() = runTest {
    val storage = FakeStorage()
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)

    assertThat(controller.mode).isEqualTo(RuntimeMode.Real)
    assertThat(storage.savedMode).isEqualTo(null)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `first read caches the saved mode without restarting`() = runTest {
    val storage = FakeStorage("Fake")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)
    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    storage.readFailure = IllegalStateException("Storage unavailable")

    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `an unknown saved mode fails instead of guessing a mode`() = runTest {
    val storage = FakeStorage("unknown")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = RuntimeModeControllerImpl(storage, restarter)

    assertFailure { controller.mode }.isInstanceOf<IllegalStateException>()
    assertFailure { controller.switchMode(RuntimeMode.Real) }.isInstanceOf<IllegalStateException>()

    assertThat(storage.savedMode).isEqualTo("unknown")
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `a failed read propagates and can be retried without caching a default`() = runTest {
    val storage = FakeStorage("Fake")
    val failure = IllegalStateException("Storage unavailable")
    storage.readFailure = failure
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = RuntimeModeControllerImpl(storage, restarter)

    assertFailure { controller.mode }.isSameInstanceAs(failure)

    storage.readFailure = null

    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `selecting the saved mode does not restart`() = runTest {
    val storage = FakeStorage("Fake")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)

    controller.switchMode(RuntimeMode.Fake)
    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(restarter.restartCount).isEqualTo(0)
    runCurrent()
    controller.switchMode(RuntimeMode.Fake)

    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `changing modes saves the selection for the restarted app`() = runTest {
    for (initialMode in RuntimeMode.entries) {
      val selectedMode = RuntimeMode.entries.single { it != initialMode }
      val storage = FakeStorage(initialMode.name)
      val restarter = FakeApplicationRestarter {
        assertThat(storage.savedMode).isEqualTo(selectedMode.name)
      }
      val controller: RuntimeModeController = controller(storage, restarter)
      runCurrent()

      assertThat(controller.mode).isEqualTo(initialMode)

      controller.switchMode(selectedMode)
      runCurrent()
      assertThat(controller.mode).isEqualTo(initialMode)
      assertThat(storage.savedMode).isEqualTo(selectedMode.name)
      assertThat(restarter.restartCount).isEqualTo(1)

      controller.switchMode(selectedMode)
      assertThat(controller.mode).isEqualTo(initialMode)
      assertThat(restarter.restartCount).isEqualTo(1)

      val nextController: RuntimeModeController = controller(storage, FakeApplicationRestarter())
      assertThat(nextController.mode).isEqualTo(selectedMode)
    }
  }

  @Test
  fun `switching before the first read keeps the initial mode active until restart`() = runTest {
    val storage = FakeStorage("Fake")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)

    controller.switchMode(RuntimeMode.Real)
    runCurrent()
    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(storage.savedMode).isEqualTo("Real")
    assertThat(restarter.restartCount).isEqualTo(1)

    val nextController: RuntimeModeController = controller(storage, FakeApplicationRestarter())
    assertThat(nextController.mode).isEqualTo(RuntimeMode.Real)
  }

  @Test
  fun `a failed write preserves the current mode and does not restart`() = runTest {
    val storage = FakeStorage("Fake")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)
    runCurrent()
    storage.writeFailure = IllegalStateException("Storage unavailable")

    assertFailure { controller.switchMode(RuntimeMode.Real) }.isInstanceOf<IllegalStateException>()

    assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
    assertThat(storage.savedMode).isEqualTo("Fake")
    assertThat(restarter.restartCount).isEqualTo(0)
  }

  @Test
  fun `concurrent requests for the same mode restart once`() = runTest {
    val storage = FakeStorage("Real")
    val restarter = FakeApplicationRestarter()
    val controller: RuntimeModeController = controller(storage, restarter)
    runCurrent()
    val writeBarrier = CompletableDeferred<Unit>()
    storage.writeBarrier = writeBarrier

    val requests = List(2) { async { controller.switchMode(RuntimeMode.Fake) } }
    runCurrent()
    writeBarrier.complete(Unit)
    requests.awaitAll()

    assertThat(controller.mode).isEqualTo(RuntimeMode.Real)
    assertThat(storage.savedMode).isEqualTo("Fake")
    assertThat(restarter.restartCount).isEqualTo(1)
  }

  private fun controller(
    storage: RuntimeModeStorage,
    restarter: ApplicationRestarter,
  ): RuntimeModeController = RuntimeModeControllerImpl(storage, restarter)

  private class FakeApplicationRestarter(private val onRestart: () -> Unit = {}) :
    ApplicationRestarter {
    var restartCount = 0
      private set

    override fun restart() {
      onRestart()
      restartCount++
    }
  }

  private class FakeStorage(private var selectedMode: String? = null) : RuntimeModeStorage {
    var readFailure: Exception? = null
    var writeFailure: Exception? = null
    var writeBarrier: CompletableDeferred<Unit>? = null

    val savedMode: String?
      get() = selectedMode

    override fun read(): String? {
      readFailure?.let { throw it }
      return selectedMode
    }

    override suspend fun write(mode: String) {
      writeBarrier?.await()
      writeFailure?.let { throw it }
      selectedMode = mode
    }
  }
}
