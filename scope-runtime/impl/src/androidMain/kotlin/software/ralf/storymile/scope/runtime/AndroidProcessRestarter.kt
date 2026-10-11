package software.ralf.storymile.scope.runtime

import android.app.Application
import com.jakewharton.processphoenix.ProcessPhoenix
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding

/** Relaunches the Android app in a new process. */
@ContributesBinding(AppScope::class)
class AndroidProcessRestarter(private val application: Application) : ProcessRestarter {
  override fun restart() {
    ProcessPhoenix.triggerRebirth(application)
  }
}
