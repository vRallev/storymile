package software.ralf.storymile.playlist

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealInitialPlaylists : InitialPlaylists {
  override fun get(): List<Playlist> = emptyList()
}
