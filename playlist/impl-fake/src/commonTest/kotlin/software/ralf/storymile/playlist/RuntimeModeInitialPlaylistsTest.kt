package software.ralf.storymile.playlist

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import software.ralf.storymile.podcast.BundledPodcastFixtures
import software.ralf.storymile.runtimemode.RuntimeMode
import software.ralf.storymile.runtimemode.testing.FakeRuntimeModeController

class RuntimeModeInitialPlaylistsTest {
  @Test
  fun `initial playlists come from the provider for the selected runtime mode`() {
    val fake: InitialPlaylists =
      RuntimeModeInitialPlaylists(
        runtimeModeController = FakeRuntimeModeController(RuntimeMode.Fake),
        real = lazy { error("Real provider must stay unused in Fake mode") },
        fake = lazy { FakeInitialPlaylists(BundledPodcastFixtures.queue.map { it.episode.id }) },
      )
    val real: InitialPlaylists =
      RuntimeModeInitialPlaylists(
        runtimeModeController = FakeRuntimeModeController(RuntimeMode.Real),
        real = lazy { RealInitialPlaylists() },
        fake = lazy { error("Fake provider must stay unused in Real mode") },
      )

    val sample = fake.get().single()
    assertThat(sample.id.value).isEqualTo("storymile-test-episodes")
    assertThat(sample.name.value).isEqualTo("Storymile test episodes")
    assertThat(sample.episodeIds.map { it.value }).isEqualTo((1..11).map { "test-episode-$it" })
    assertThat(real.get()).isEqualTo(emptyList())
  }
}
