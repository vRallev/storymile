package software.ralf.storymile.runtimemode

import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import software.ralf.app.platform.inject.metro.ContributesScoped
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.launch
import software.ralf.app.platform.scope.register
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.storage.ScopedStorage

@SingleIn(AppScope::class)
@ContributesScoped(AppScope::class)
class RuntimeModeControllerImpl(
  @ForScope(RuntimeModeController::class) private val storage: ScopedStorage,
  private val applicationRestarter: ApplicationRestarter,
) : RuntimeModeController, Scoped {
  private val modeKey = stringPreferencesKey("mode")
  private var initialMode: RuntimeMode? = null
  private var enteredScope = false
  private val initialized = CompletableDeferred<Unit>()
  private val requests = Channel<RuntimeMode>(Channel.CONFLATED)

  override val mode: StateFlow<RuntimeMode>
    field = MutableStateFlow(RuntimeMode.DEFAULT)

  override fun onEnterScope(scope: Scope) {
    enteredScope = true
    scope.register(storage)
    scope
      .launch {
        val startupMode = initialMode
        if (startupMode == null) {
          mode.value = readSavedMode()
        } else {
          persistMode(startupMode)
        }
        initialized.complete(Unit)

        for (requestedMode in requests) {
          if (requestedMode != mode.value && persistMode(requestedMode)) {
            requests.close()
            applicationRestarter.restart()
            break
          }
        }
      }
      .invokeOnCompletion { cause ->
        requests.cancel()
        if (cause != null) {
          initialized.completeExceptionally(cause)
        }
      }
  }

  override fun setInitialMode(mode: RuntimeMode) {
    check(!enteredScope) {
      "The initial runtime mode must be selected before the app scope starts."
    }
    initialMode = mode
    this.mode.value = mode
    initialized.complete(Unit)
  }

  override fun switchMode(mode: RuntimeMode) {
    requests.trySend(mode)
  }

  override suspend fun awaitInitialization() {
    initialized.await()
  }

  private suspend fun readSavedMode(): RuntimeMode {
    val name =
      try {
        storage.preferences("runtime-mode").data.first()[modeKey]
      } catch (exception: IOException) {
        logger.w(exception) { "Could not restore runtime mode." }
        null
      }
    return RuntimeMode.entries.firstOrNull { it.name == name } ?: RuntimeMode.DEFAULT
  }

  private suspend fun persistMode(mode: RuntimeMode): Boolean =
    try {
      storage.preferences("runtime-mode").edit { it[modeKey] = mode.name }
      true
    } catch (exception: IOException) {
      logger.w(exception) { "Could not save runtime mode." }
      false
    }
}
