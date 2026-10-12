package software.ralf.storymile.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import software.ralf.storymile.playlist.EpisodeId
import software.ralf.storymile.playlist.Playlist
import software.ralf.storymile.playlist.PlaylistId
import software.ralf.storymile.playlist.PlaylistName
import software.ralf.storymile.playlist.PlaylistRepository

internal class TestPlaylistRepository(initial: List<Playlist>) : PlaylistRepository {
  override val playlists: StateFlow<List<Playlist>>
    field = MutableStateFlow(initial)

  fun update(playlists: List<Playlist>) {
    this.playlists.value = playlists
  }

  override fun create(name: PlaylistName): Unit = error("Not used by playback")

  override fun rename(id: PlaylistId, name: PlaylistName): Unit = error("Not used by playback")

  override fun delete(id: PlaylistId): Unit = error("Not used by playback")

  override fun addEpisode(playlistId: PlaylistId, episodeId: EpisodeId): Unit =
    error("Not used by playback")

  override fun removeEpisode(playlistId: PlaylistId, episodeId: EpisodeId): Unit =
    error("Not used by playback")

  override fun moveEpisode(
    playlistId: PlaylistId,
    episodeId: EpisodeId,
    beforeEpisodeId: EpisodeId?,
  ): Unit = error("Not used by playback")
}
