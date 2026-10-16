package software.ralf.storymile.rss

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import software.ralf.storymile.http.HttpResponse
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.RuntimeModeController

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PodcastFeedServiceImpl(
  private val runtimeModeController: RuntimeModeController,
  private val real: Lazy<RssPodcastFeedService>,
  private val fake: Lazy<FakePodcastFeedService>,
) : PodcastFeedService {
  override suspend fun load(feedUrl: String): HttpResponse<PodcastFeed> =
    when (runtimeModeController.mode()) {
      RuntimeMode.Real -> real.value.load(feedUrl)
      RuntimeMode.Fake -> fake.value.load(feedUrl)
    }
}
