package software.ralf.storymile.playlist

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [RealInitialPlaylists::class])
class RuntimeModeInitialPlaylists(
  private val runtimeModeController: RuntimeModeController,
  private val real: Lazy<RealInitialPlaylists>,
  private val fake: Lazy<FakeInitialPlaylists>,
) : InitialPlaylists {
  override fun get(): List<Playlist> =
    when (runtimeModeController.mode) {
      RuntimeMode.Real -> real.value.get()
      RuntimeMode.Fake -> fake.value.get()
    }
}
