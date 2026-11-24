package software.ralf.storymile.theme

import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ForScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import software.ralf.storymile.logging.Logger.Companion.logger
import software.ralf.storymile.storage.Storage

@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class StoredThemeController(
  @ForScope(AppScope::class) storage: Storage,
  @ForScope(AppScope::class) coroutineScope: CoroutineScope,
) : ThemeController {
  private val preferences = storage.preferences("appearance")
  private val changes = Channel<(ThemeMode) -> ThemeMode>(Channel.UNLIMITED)
  private val initialModeLoaded = CompletableDeferred<Unit>()

  override val mode: StateFlow<ThemeMode>
    field = MutableStateFlow(ThemeMode.SYSTEM)

  init {
    coroutineScope
      .launch {
        mode.value = readMode()
        initialModeLoaded.complete(Unit)
        for (change in changes) {
          val selection = change(mode.value)
          saveMode(selection)
          mode.value = selection
        }
      }
      .invokeOnCompletion { cause ->
        changes.cancel()
        if (cause != null) {
          initialModeLoaded.completeExceptionally(cause)
        }
      }
  }

  override suspend fun awaitInitialMode() {
    initialModeLoaded.await()
  }

  override fun setMode(mode: ThemeMode) {
    changes.trySend { mode }
  }

  override fun toggle(systemDarkTheme: Boolean) {
    changes.trySend { current ->
      if (current.isDark(systemDarkTheme)) ThemeMode.LIGHT else ThemeMode.DARK
    }
  }

  private suspend fun readMode(): ThemeMode =
    try {
      when (preferences.data.first()[darkThemeKey]) {
        true -> ThemeMode.DARK
        false -> ThemeMode.LIGHT
        null -> ThemeMode.SYSTEM
      }
    } catch (exception: IOException) {
      logger.w(exception) { "Could not read the theme preference" }
      ThemeMode.SYSTEM
    }

  private suspend fun saveMode(mode: ThemeMode) {
    try {
      preferences.edit { values ->
        when (mode) {
          ThemeMode.SYSTEM -> values.remove(darkThemeKey)
          ThemeMode.LIGHT -> values[darkThemeKey] = false
          ThemeMode.DARK -> values[darkThemeKey] = true
        }
      }
    } catch (exception: IOException) {
      logger.w(exception) { "Could not save the theme preference" }
    }
  }

  private companion object {
    val darkThemeKey = booleanPreferencesKey("dark")
  }
}
