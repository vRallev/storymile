package software.ralf.storymile.playlist.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.Playlist
import software.ralf.storymile.playlist.PlaylistId
import software.ralf.storymile.playlist.PlaylistName
import software.ralf.storymile.playlist.PlaylistRepository

/** Publishes controlled snapshots. Mutation commands fail. */
class FakePlaylistRepository(initial: List<Playlist> = emptyList()) : PlaylistRepository {
  override val playlists: StateFlow<List<Playlist>>
    field = MutableStateFlow(initial)

  fun update(playlists: List<Playlist>) {
    this.playlists.value = playlists
  }

  override fun create(name: PlaylistName): Unit = error("Playlist mutations are not configured")

  override fun rename(id: PlaylistId, name: PlaylistName): Unit =
    error("Playlist mutations are not configured")

  override fun delete(id: PlaylistId): Unit = error("Playlist mutations are not configured")

  override fun addEpisode(playlistId: PlaylistId, episodeId: EpisodeId): Unit =
    error("Playlist mutations are not configured")

  override fun removeEpisode(playlistId: PlaylistId, episodeId: EpisodeId): Unit =
    error("Playlist mutations are not configured")

  override fun moveEpisode(
    playlistId: PlaylistId,
    episodeId: EpisodeId,
    beforeEpisodeId: EpisodeId?,
  ): Unit = error("Playlist mutations are not configured")
}
