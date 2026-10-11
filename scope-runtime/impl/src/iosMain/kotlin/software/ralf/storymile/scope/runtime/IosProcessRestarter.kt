package software.ralf.storymile.scope.runtime

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import platform.posix.exit

/** Terminates the iOS app without a dialog. The user must launch it again. */
@ContributesBinding(AppScope::class)
class IosProcessRestarter : ProcessRestarter {
  override fun restart() {
    exit(0)
  }
}
