package software.ralf.storymile.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastDiscoveryServiceImpl(
  private val runtimeModeController: RuntimeModeController,
  private val real: Lazy<ApplePodcastDiscoveryService>,
  private val fake: Lazy<FakePodcastDiscoveryService>,
) : PodcastDiscoveryService {
  override suspend fun search(query: String): HttpResponse<List<DiscoveredPodcast>> =
    when (runtimeModeController.mode) {
      RuntimeMode.Real -> real.value.search(query)
      RuntimeMode.Fake -> fake.value.search(query)
    }

  override suspend fun lookup(id: String): HttpResponse<List<DiscoveredPodcast>> =
    when (runtimeModeController.mode) {
      RuntimeMode.Real -> real.value.lookup(id)
      RuntimeMode.Fake -> fake.value.lookup(id)
    }

  override suspend fun popular(): HttpResponse<List<DiscoveredPodcast>> =
    when (runtimeModeController.mode) {
      RuntimeMode.Real -> real.value.popular()
      RuntimeMode.Fake -> fake.value.popular()
    }
}
