package software.ralf.storymile.templates

import androidx.compose.ui.input.key.Key
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@ContributesBinding(AppScope::class, replaces = [NoAppShortcuts::class])
class FakeModeAppShortcuts(private val controller: RuntimeModeController) : AppShortcuts {
  override val logoLongPress: suspend () -> Unit = {
    val mode =
      when (controller.mode) {
        RuntimeMode.Real -> RuntimeMode.Fake
        RuntimeMode.Fake -> RuntimeMode.Real
      }
    controller.switchMode(mode)
  }

  override fun actionForKey(key: Key): (suspend () -> Unit)? = logoLongPress.takeIf { key == Key.F }
}
