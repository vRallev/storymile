package software.ralf.storymile.playlist

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named

@Inject
class FakeInitialPlaylists(@Named("fakePodcastEpisodeIds") private val episodeIds: List<String>) :
  InitialPlaylists {
  override fun get(): List<Playlist> =
    listOf(
      Playlist(
        id = PlaylistId("storymile-test-episodes"),
        name = PlaylistName("Storymile test episodes"),
        episodeIds = episodeIds.map { EpisodeId(it) },
      ),
    )
}
