package software.ralf.storymile.playlist

import dev.zacsweers.metro.ForScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot
import software.ralf.storymile.user.UserScope

/** Controls the current user's playlists through the production repository. */
@ContributesRobot(UserScope::class)
class PlaylistRobot(
  private val repository: PlaylistRepository,
  @ForScope(UserScope::class) private val coroutineScope: CoroutineScope,
) : ComposeRobot() {
  /** Enqueues a playlist with one episode. Wait for playback before interacting with it. */
  fun addPlayablePlaylist() {
    coroutineScope.launch {
      val count = repository.playlists.value.size
      repository.create(PlaylistName("Test playlist"))
      val playlist = repository.playlists.first { it.size == count + 1 }.last()
      repository.addEpisode(playlist.id, EpisodeId("test-episode"))
    }
  }

  /** Enqueues removal of all playlists. Wait for playback to disappear before checking layout. */
  fun clearPlaylists() {
    repository.playlists.value.forEach { repository.delete(it.id) }
  }
}
