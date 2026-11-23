package software.ralf.storymile.player

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAudioSourceResolver : AudioSourceResolver {
  override fun resolve(enclosureUrl: String): String = enclosureUrl
}
