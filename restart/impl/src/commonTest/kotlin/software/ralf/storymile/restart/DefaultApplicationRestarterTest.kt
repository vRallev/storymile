package software.ralf.storymile.restart

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.RootScopeProvider
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.onExit
import software.ralf.app.platform.scope.runTestWithScope

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultApplicationRestarterTest {
  @Test
  fun `restart waits for the outgoing caller to finish cleanup`() = runTestWithScope { scope ->
    var cleanupFinished = false
    var restarted = false
    val restarter: ApplicationRestarter =
      restarter(scope) {
        assertThat(scope.isDestroyed()).isTrue()
        assertThat(cleanupFinished).isTrue()
        restarted = true
      }

    scope.launch {
      try {
        restarter.restart()
        awaitCancellation()
      } finally {
        withContext(NonCancellable) {
          delay(100)
          cleanupFinished = true
        }
      }
    }
    runCurrent()

    assertThat(scope.isDestroyed()).isTrue()
    assertThat(restarted).isFalse()
    advanceTimeBy(100)
    runCurrent()
    assertThat(restarted).isTrue()
  }

  @Test
  fun `restart proceeds after five seconds despite noncancellable cleanup`() =
    runTestWithScope { scope ->
      val cleanup = CompletableDeferred<Unit>()
      var cleanupFinished = false
      var restarted = false
      val restarter: ApplicationRestarter = restarter(scope) { restarted = true }
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

      try {
        runCurrent()
        restarter.restart()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertThat(restarted).isFalse()

        advanceTimeBy(1)
        runCurrent()
        assertThat(restarted).isTrue()
        assertThat(cleanupFinished).isFalse()
      } finally {
        cleanup.complete(Unit)
      }
    }

  @Test
  fun `repeated requests trigger only one restart`() = runTestWithScope { scope ->
    var restartCount = 0
    val restarter: ApplicationRestarter = restarter(scope) { restartCount += 1 }
    scope.launch {
      try {
        awaitCancellation()
      } finally {
        withContext(NonCancellable) { delay(100) }
      }
    }
    runCurrent()

    repeat(3) { restarter.restart() }
    runCurrent()
    repeat(3) { restarter.restart() }
    advanceTimeBy(100)
    runCurrent()
    restarter.restart()
    runCurrent()

    assertThat(restartCount).isEqualTo(1)
  }

  @Test
  fun `cleanup failure does not prevent restart`() = runTestWithScope { scope ->
    var restarted = false
    val restarter: ApplicationRestarter = restarter(scope) { restarted = true }
    scope.onExit { error("Cleanup failed") }

    restarter.restart()
    runCurrent()

    assertThat(restarted).isTrue()
  }

  private fun TestScope.restarter(scope: Scope, restart: () -> Unit): ApplicationRestarter {
    return DefaultApplicationRestarter(
      rootScopeProvider =
        object : RootScopeProvider {
          override val rootScope: Scope = scope
        },
      processRestarter = { restart() },
      mainDispatcher = StandardTestDispatcher(testScheduler),
    )
  }
}
