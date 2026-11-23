package software.ralf.storymile.player

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [RealAudioSourceResolver::class])
class RuntimeModeAudioSourceResolver(
  private val runtimeModeController: RuntimeModeController,
  private val real: Lazy<RealAudioSourceResolver>,
  private val fake: Lazy<FakeAudioSourceResolver>,
) : AudioSourceResolver {
  override fun resolve(enclosureUrl: String): String =
    when (runtimeModeController.mode) {
      RuntimeMode.Real -> real.value.resolve(enclosureUrl)
      RuntimeMode.Fake -> fake.value.resolve(enclosureUrl)
    }
}
