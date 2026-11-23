package software.ralf.storymile.player

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named

@Inject
class FakeAudioSourceResolver(
  @Named("fakePodcastAudioUri") private val audioUri: (String) -> String?,
) : AudioSourceResolver {
  override fun resolve(enclosureUrl: String): String = audioUri(enclosureUrl) ?: enclosureUrl
}
