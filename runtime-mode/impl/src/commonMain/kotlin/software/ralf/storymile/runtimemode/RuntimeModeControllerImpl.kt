package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import software.ralf.storymile.restart.ApplicationRestarter

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RuntimeModeControllerImpl(
  private val storage: RuntimeModeStorage,
  private val applicationRestarter: ApplicationRestarter,
) : RuntimeModeController {
  private val mutex = Mutex()
  private val loadedMode = lazy { readMode() }

  override val mode: RuntimeMode by loadedMode

  override suspend fun switchMode(mode: RuntimeMode) {
    mutex.withLock {
      val savedMode = if (loadedMode.isInitialized()) readMode() else this.mode
      if (savedMode == mode) {
        return
      }

      storage.write(mode.name)
      applicationRestarter.restart()
    }
  }

  private fun readMode(): RuntimeMode {
    val savedMode = storage.read() ?: return RuntimeMode.Real
    return checkNotNull(RuntimeMode.entries.firstOrNull { it.name == savedMode }) {
      "Unknown runtime mode: $savedMode"
    }
  }
}
