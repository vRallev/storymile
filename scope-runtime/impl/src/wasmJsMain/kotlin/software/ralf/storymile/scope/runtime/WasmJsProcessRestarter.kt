package software.ralf.storymile.scope.runtime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import kotlin.js.js

/** Reloads the page to create a fresh application runtime. */
@ContributesBinding(AppScope::class)
class WasmJsProcessRestarter : ProcessRestarter {
  override fun restart() {
    reloadPage()
  }
}

private fun reloadPage(): Unit = js("window.location.reload()")
