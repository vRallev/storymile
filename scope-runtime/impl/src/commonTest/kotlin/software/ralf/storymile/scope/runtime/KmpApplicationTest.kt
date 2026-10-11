package software.ralf.storymile.scope.runtime

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.onExit

@OptIn(ExperimentalCoroutinesApi::class)
class KmpApplicationTest {
  @Test
  fun `restart from an app coroutine waits for scope and child cleanup`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    var scopeExited = false
    var childExited = false
    var scopeJobFinished = false
    var childJobFinished = false
    var restartCallerFinished = false
    lateinit var childScope: Scope

    try {
      withApplication(
        services = { application ->
          setOf(
            object : Scoped {
              override fun onEnterScope(scope: Scope) {
                assertSame(scope, application.rootScope)
                childScope =
                  scope.buildChild("child") { addCoroutineScopeScoped(coroutineOwner("Child")) }
                childScope.onExit { childExited = true }
                scope.launch {
                  try {
                    awaitCancellation()
                  } finally {
                    withContext(NonCancellable) {
                      cleanup.await()
                      scopeJobFinished = true
                    }
                  }
                }
                childScope.launch {
                  try {
                    awaitCancellation()
                  } finally {
                    withContext(NonCancellable) {
                      cleanup.await()
                      childJobFinished = true
                    }
                  }
                }
              }

              override fun onExitScope() {
                scopeExited = true
              }
            },
          )
        },
      ) { application, host ->
        val scope = application.rootScope
        scope.launch {
          try {
            application.restart()
            awaitCancellation()
          } finally {
            restartCallerFinished = true
          }
        }
        runCurrent()

        assertThat(scope.isDestroyed()).isTrue()
        assertThat(childScope.isDestroyed()).isTrue()
        assertThat(scopeExited).isTrue()
        assertThat(childExited).isTrue()
        assertThat(restartCallerFinished).isTrue()
        assertFailsWith<IllegalStateException> { application.rootScope }
        assertThat(host.restartCount).isEqualTo(0)
        assertThat(scopeJobFinished).isFalse()
        assertThat(childJobFinished).isFalse()

        advanceTimeBy(1_000)
        cleanup.complete(Unit)
        runCurrent()

        assertThat(scopeJobFinished).isTrue()
        assertThat(childJobFinished).isTrue()
        assertThat(host.restartCount).isEqualTo(1)
        assertThat(host.graphCount).isEqualTo(1)
      }
    } finally {
      cleanup.complete(Unit)
    }
  }

  @Test
  fun `restart proceeds after five seconds even when cleanup cannot be canceled`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    var cleanupFinished = false
    try {
      withApplication(
        services = {
          setOf(
            object : Scoped {
              override fun onEnterScope(scope: Scope) {
                scope.launch {
                  try {
                    awaitCancellation()
                  } finally {
                    withContext(NonCancellable) {
                      cleanup.await()
                      cleanupFinished = true
                    }
                  }
                }
              }
            },
          )
        },
      ) { application, host ->
        runCurrent()
        application.restart()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()

        assertThat(host.restartCount).isEqualTo(0)
        advanceTimeBy(1)
        runCurrent()

        assertThat(host.restartCount).isEqualTo(1)
        assertThat(cleanupFinished).isFalse()
        assertThat(host.graphCount).isEqualTo(1)
        cleanup.complete(Unit)
        runCurrent()
        assertThat(cleanupFinished).isTrue()
      }
    } finally {
      cleanup.complete(Unit)
    }
  }

  @Test
  fun `restart requests combine while queued and while cleanup runs`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    try {
      withApplication(services = { setOf(waitingService(cleanup)) }) { application, host ->
        runCurrent()
        repeat(3) { application.restart() }
        runCurrent()
        repeat(3) { application.restart() }
        cleanup.complete(Unit)
        runCurrent()
        application.restart()
        runCurrent()

        assertThat(host.restartCount).isEqualTo(1)
        assertThat(host.graphCount).isEqualTo(1)
      }
    } finally {
      cleanup.complete(Unit)
    }
  }

  @Test
  fun `shutdown during cleanup cancels the hard restart`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    try {
      withApplication(services = { setOf(waitingService(cleanup)) }) { application, host ->
        runCurrent()
        application.restart()
        runCurrent()
        host.destroy()
        advanceTimeBy(5_000)
        cleanup.complete(Unit)
        application.restart()
        runCurrent()

        assertThat(host.restartCount).isEqualTo(0)
        assertFailsWith<IllegalStateException> { application.rootScope }
      }
    } finally {
      cleanup.complete(Unit)
    }
  }

  @Test
  fun `shutdown cancels a queued restart`() = runTest {
    withApplication { application, host ->
      val scope = application.rootScope
      application.restart()
      host.destroy()
      runCurrent()

      assertThat(scope.isDestroyed()).isTrue()
      assertThat(host.restartCount).isEqualTo(0)
    }
  }

  @Test
  fun `restart proceeds when synchronous scope cleanup fails`() = runTest {
    withApplication { application, host ->
      application.rootScope.onExit { error("Cleanup failed") }
      application.restart()
      runCurrent()

      assertThat(host.restartCount).isEqualTo(1)
      assertThat(host.graphCount).isEqualTo(1)
    }
  }

  @Test
  fun `restart requests before startup and after shutdown are ignored`() = runTest {
    val host = TestHost(this)
    val application: Application = host.application
    application.restart()
    runCurrent()
    assertThat(host.graphCount).isEqualTo(0)
    assertThat(host.restartCount).isEqualTo(0)

    host.create()
    host.destroy()
    application.restart()
    runCurrent()
    assertThat(host.restartCount).isEqualTo(0)
    assertFailsWith<IllegalStateException> { application.rootScope }
  }

  private fun waitingService(cleanup: CompletableDeferred<Unit>): Scoped {
    return object : Scoped {
      override fun onEnterScope(scope: Scope) {
        scope.launch {
          try {
            awaitCancellation()
          } finally {
            withContext(NonCancellable) { cleanup.await() }
          }
        }
      }
    }
  }

  private suspend fun TestScope.withApplication(
    services: (Application) -> Set<Scoped> = { emptySet() },
    block: suspend (Application, TestHost) -> Unit,
  ) {
    val host = TestHost(this, services)
    val application: Application = host.application
    try {
      host.create()
      block(application, host)
    } finally {
      host.destroy()
    }
  }

  private fun TestScope.coroutineOwner(name: String): CoroutineScopeScoped {
    return CoroutineScopeScoped(
      StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineName(name),
    )
  }

  private inner class TestHost(
    testScope: TestScope,
    services: (Application) -> Set<Scoped> = { emptySet() },
  ) {
    var graphCount = 0
      private set

    var restartCount = 0
      private set

    private val owner =
      KmpApplication(
        graphFactory = { application ->
          graphCount += 1
          TestGraph(
            appScopedInstances = services(application),
            appScopeCoroutineScopeScoped = testScope.coroutineOwner("App"),
            processRestarter = { restartCount += 1 },
          )
        },
        dispatcher = StandardTestDispatcher(testScope.testScheduler),
      )

    val application: Application = owner

    fun create() {
      owner.create()
    }

    fun destroy() {
      owner.destroy()
    }
  }

  private class TestGraph(
    override val appScopedInstances: Set<Scoped>,
    override val appScopeCoroutineScopeScoped: CoroutineScopeScoped,
    override val processRestarter: ProcessRestarter,
  ) : ApplicationGraph
}
