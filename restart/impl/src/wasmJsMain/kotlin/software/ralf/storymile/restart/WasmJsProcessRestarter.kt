package software.ralf.storymile.restart

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.js

/** Reloads the page to create a fresh application runtime. */
@ContributesBinding(AppScope::class)
class WasmJsProcessRestarter : ProcessRestarter {
  override fun restart() {
    reloadPage()
  }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun reloadPage(): Unit = js("window.location.reload()")
