package software.ralf.storymile

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped

class KmpApplicationTest {
  @Test
  fun `lifecycle registration can access the application root scope`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var entered = false
    val application =
      KmpApplication(dispatcher) { rootScopeProvider ->
        val service =
          object : Scoped {
            override fun onEnterScope(scope: Scope) {
              assertThat(rootScopeProvider.rootScope).isSameInstanceAs(scope)
              entered = true
            }
          }
        StartupTestGraph(dispatcher = dispatcher, appScopedInstances = setOf(service))
      }
    try {
      application.awaitReady()
      assertThat(entered).isTrue()
    } finally {
      application.destroy()
    }
  }

  @Test
  fun `closing before initialization does not create a graph`() = runTest {
    var creations = 0
    val dispatcher = StandardTestDispatcher(testScheduler)
    val application =
      KmpApplication(dispatcher) {
        creations++
        StartupTestGraph(dispatcher)
      }
    application.destroy()
    assertFailure { application.awaitReady() }.isInstanceOf<CancellationException>()
    assertThat(creations).isEqualTo(0)
    assertFailure { application.rootScope }.isInstanceOf<IllegalStateException>()
    application.destroy()
  }

  @Test
  fun `construction is idle and concurrent hosts share initialization`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val release = CompletableDeferred<Unit>()
    var creations = 0
    val application =
      KmpApplication(dispatcher) {
        creations++
        release.await()
        StartupTestGraph(dispatcher)
      }
    try {
      runCurrent()
      assertThat(creations).isEqualTo(0)
      assertFailure { application.rootScope }.isInstanceOf<IllegalStateException>()

      val firstHost = async { application.awaitReady() }
      val secondHost = async { application.awaitReady() }
      runCurrent()
      assertThat(creations).isEqualTo(1)
      assertThat(firstHost.isCompleted).isFalse()
      assertThat(secondHost.isCompleted).isFalse()
      firstHost.cancel()

      release.complete(Unit)
      secondHost.await()
      assertThat(application.rootScope.isDestroyed()).isFalse()
      assertThat(creations).isEqualTo(1)
    } finally {
      application.destroy()
    }
  }

  @Test
  fun `closing during uncancellable initialization releases the late scope`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val release = CompletableDeferred<Unit>()
    var entered = false
    var exited = false
    val service =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          entered = true
        }

        override fun onExitScope() {
          exited = true
        }
      }
    val application =
      KmpApplication(dispatcher) {
        withContext(NonCancellable) { release.await() }
        StartupTestGraph(dispatcher = dispatcher, appScopedInstances = setOf(service))
      }
    val host = async { application.awaitReady() }
    runCurrent()
    application.destroy()
    release.complete(Unit)
    runCurrent()
    assertThat(entered).isTrue()
    assertThat(exited).isTrue()
    assertThat(host.isCancelled).isTrue()
    assertFailure { application.rootScope }.isInstanceOf<IllegalStateException>()
    application.destroy()
  }

  @Test
  fun `a registration failure releases partially initialized services`() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    var exited = false
    val service =
      object : Scoped {
        override fun onExitScope() {
          exited = true
        }
      }
    val brokenService =
      object : Scoped {
        override fun onEnterScope(scope: Scope) {
          error("Startup failed")
        }
      }
    val application =
      KmpApplication(dispatcher) {
        StartupTestGraph(
          dispatcher = dispatcher,
          appScopedInstances = linkedSetOf(service, brokenService),
        )
      }
    try {
      assertFailure { application.awaitReady() }.isInstanceOf<IllegalStateException>()
      assertThat(exited).isTrue()
      assertFailure { application.rootScope }.isInstanceOf<IllegalStateException>()
    } finally {
      application.destroy()
    }
  }
}
