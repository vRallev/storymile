package software.ralf.storymile.player

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import software.ralf.storymile.podcast.BundledPodcastFixtures
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class RuntimeModeAudioSourceResolverTest {
  @Test
  fun `fake mode resolves bundled audio and real mode delegates to the normal resolver`() {
    val enclosureUrl = BundledPodcastFixtures.queue.first().episode.enclosure.url
    val remoteUrl = "https://example.com/episode.mp3"
    val fake: AudioSourceResolver =
      RuntimeModeAudioSourceResolver(
        runtimeModeController = FakeRuntimeModeController(RuntimeMode.Fake),
        real = lazy { error("Real resolver must stay unused in Fake mode") },
        fake = lazy { FakeAudioSourceResolver { BundledPodcastFixtures.audioUri(it) } },
      )
    val real: AudioSourceResolver =
      RuntimeModeAudioSourceResolver(
        runtimeModeController = FakeRuntimeModeController(RuntimeMode.Real),
        real = lazy { RealAudioSourceResolver() },
        fake = lazy { error("Fake resolver must stay unused in Real mode") },
      )

    assertThat(fake.resolve(enclosureUrl))
      .isEqualTo(checkNotNull(BundledPodcastFixtures.audioUri(enclosureUrl)))
    assertThat(fake.resolve(remoteUrl)).isEqualTo(remoteUrl)
    assertThat(real.resolve(enclosureUrl)).isEqualTo(enclosureUrl)
  }
}
