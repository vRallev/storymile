package software.ralf.storymile

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.app.platform.scope.Scoped
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.theme.ThemeController
import software.ralf.storymile.theme.ThemeMode
import software.ralf.storymile.util.Platform

internal class StartupTestGraph(
  dispatcher: CoroutineDispatcher,
  override val appScopedInstances: Set<Scoped> = emptySet(),
  loadTheme: suspend () -> ThemeMode = { ThemeMode.SYSTEM },
) : AppGraph {
  override val platform: Platform = Platform.Desktop.Linux
  override val storage: Storage
    get() = error("Startup lifecycle tests do not use storage.")

  override val themeController: ThemeController =
    object : ThemeController {
      private val currentMode = MutableStateFlow(ThemeMode.SYSTEM)
      override val mode: StateFlow<ThemeMode> = currentMode

      override suspend fun awaitInitialMode() {
        currentMode.value = loadTheme()
      }

      override fun setMode(mode: ThemeMode) = Unit

      override fun toggle(systemDarkTheme: Boolean) = Unit
    }

  override val appScopeCoroutineScopeScoped =
    CoroutineScopeScoped(dispatcher + SupervisorJob() + CoroutineName("StartupTest"))
}
