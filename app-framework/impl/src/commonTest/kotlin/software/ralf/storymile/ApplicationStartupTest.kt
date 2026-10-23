package software.ralf.storymile

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped

class ApplicationStartupTest {
  @Test
  fun `construction is idle and concurrent hosts share initialization`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val release = CompletableDeferred<Unit>()
    var creations = 0
    val startup = ApplicationStartup(dispatcher) {
      creations++
      release.await()
      StartupTestGraph(dispatcher)
    }
    try {
      runCurrent()
      assertThat(creations).isEqualTo(0)
      assertFailure { startup.rootScope }.isInstanceOf<IllegalStateException>()

      val firstHost = async { startup.awaitReady() }
      val secondHost = async { startup.awaitReady() }
      runCurrent()
      assertThat(creations).isEqualTo(1)
      assertThat(firstHost.isCompleted).isFalse()
      assertThat(secondHost.isCompleted).isFalse()
      firstHost.cancel()

      release.complete(Unit)
      secondHost.await()
      assertThat(startup.rootScope.isDestroyed()).isFalse()
      assertThat(creations).isEqualTo(1)
    } finally {
      startup.destroy()
    }
  }

  @Test
  fun `closing during uncancellable initialization releases the late scope`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val release = CompletableDeferred<Unit>()
    var entered = false
    var exited = false
    val service = object : Scoped {
      override fun onEnterScope(scope: Scope) {
        entered = true
      }

      override fun onExitScope() {
        exited = true
      }
    }
    val startup = ApplicationStartup(dispatcher) {
      withContext(NonCancellable) { release.await() }
      StartupTestGraph(dispatcher = dispatcher, appScopedInstances = setOf(service))
    }
    val host = async { startup.awaitReady() }
    runCurrent()
    startup.destroy()
    release.complete(Unit)
    runCurrent()
    assertThat(entered).isTrue()
    assertThat(exited).isTrue()
    assertThat(host.isCancelled).isTrue()
    assertFailure { startup.rootScope }.isInstanceOf<IllegalStateException>()
    startup.destroy()
  }

  @Test
  fun `a registration failure releases partially initialized services`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var exited = false
    val service = object : Scoped {
      override fun onExitScope() {
        exited = true
      }
    }
    val brokenService = object : Scoped {
      override fun onEnterScope(scope: Scope) {
        error("Startup failed")
      }
    }
    val startup = ApplicationStartup(dispatcher) {
      StartupTestGraph(
        dispatcher = dispatcher,
        appScopedInstances = linkedSetOf(service, brokenService),
      )
    }
    try {
      assertFailure { startup.awaitReady() }.isInstanceOf<IllegalStateException>()
      assertThat(exited).isTrue()
      assertFailure { startup.rootScope }.isInstanceOf<IllegalStateException>()
    } finally {
      startup.destroy()
    }
  }
}
