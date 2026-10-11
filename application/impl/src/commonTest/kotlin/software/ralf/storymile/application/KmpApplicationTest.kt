package software.ralf.storymile.application

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
  fun `restart waits for scope and child coroutine cleanup before creating the new graph`() =
    runTest {
      val cleanup = CompletableDeferred<Unit>()
      var scopeExited = false
      var childExited = false
      var scopeJobFinished = false
      var childJobFinished = false
      var restartCallerFinished = false
      lateinit var childScope: Scope

      withApplication(
        services = { application, generation ->
          if (generation > 1) {
            assertThat(scopeExited).isTrue()
            assertThat(childExited).isTrue()
            assertThat(scopeJobFinished).isTrue()
            assertThat(childJobFinished).isTrue()
            assertThat(restartCallerFinished).isTrue()
          }
          setOf(
            object : Scoped {
              override fun onEnterScope(scope: Scope) {
                assertSame(scope, application.rootScope)
                assertThat(application.rootScopes.value).isNull()
                if (generation == 1) {
                  childScope =
                    scope.buildChild("child") {
                      addCoroutineScopeScoped(coroutineOwner("Child"))
                    }
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
              }

              override fun onExitScope() {
                scopeExited = true
              }
            },
          )
        },
      ) { application, host ->
        val firstScope = application.rootScope
        firstScope.launch {
          try {
            application.restart()
            awaitCancellation()
          } finally {
            restartCallerFinished = true
          }
        }
        runCurrent()

        assertThat(firstScope.isDestroyed()).isTrue()
        assertThat(childScope.isDestroyed()).isTrue()
        assertThat(application.rootScopes.value).isNull()
        assertFailsWith<IllegalStateException> { application.rootScope }
        assertThat(host.graphCount).isEqualTo(1)
        assertThat(scopeJobFinished).isFalse()
        assertThat(childJobFinished).isFalse()

        cleanup.complete(Unit)
        runCurrent()

        assertThat(host.graphCount).isEqualTo(2)
        assertNotSame(firstScope, application.rootScope)
        assertSame(application.rootScope, application.rootScopes.value)
      }
    }

  @Test
  fun `restart requests combine while queued and while cleanup runs`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    withApplication(
      services = { _, generation ->
        setOf(
          object : Scoped {
            override fun onEnterScope(scope: Scope) {
              if (generation == 1) {
                scope.launch {
                  try {
                    awaitCancellation()
                  } finally {
                    withContext(NonCancellable) { cleanup.await() }
                  }
                }
              }
            }
          },
        )
      },
    ) { application, host ->
      runCurrent()
      repeat(3) { application.restart() }
      runCurrent()
      repeat(3) { application.restart() }
      cleanup.complete(Unit)
      runCurrent()

      assertThat(host.graphCount).isEqualTo(2)
      val secondScope = application.rootScope
      application.restart()
      runCurrent()

      assertThat(host.graphCount).isEqualTo(3)
      assertThat(secondScope.isDestroyed()).isTrue()
      assertNotSame(secondScope, application.rootScope)
    }
  }

  @Test
  fun `shutdown during restart prevents creation after cleanup`() = runTest {
    val cleanup = CompletableDeferred<Unit>()
    var cleanupFinished = false
    withApplication(
      services = { _, _ ->
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
      host.destroy()
      cleanup.complete(Unit)
      application.restart()
      runCurrent()

      assertThat(cleanupFinished).isTrue()
      assertThat(host.graphCount).isEqualTo(1)
      assertThat(application.rootScopes.value).isNull()
      assertFailsWith<IllegalStateException> { application.rootScope }
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
      assertThat(host.graphCount).isEqualTo(1)
      assertThat(application.rootScopes.value).isNull()
    }
  }

  private suspend fun TestScope.withApplication(
    services: (Application, Int) -> Set<Scoped> = { _, _ -> emptySet() },
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
    services: (Application, Int) -> Set<Scoped>,
  ) {
    var graphCount = 0
      private set

    private val owner =
      KmpApplication(
        graphFactory = { application ->
          graphCount += 1
          TestGraph(
            appScopedInstances = services(application, graphCount),
            appScopeCoroutineScopeScoped = testScope.coroutineOwner("App-$graphCount"),
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
  ) : ApplicationGraph
}
