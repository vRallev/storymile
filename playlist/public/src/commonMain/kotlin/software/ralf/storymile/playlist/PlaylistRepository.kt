package software.ralf.storymile.playlist

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the current user's saved playlists independently of the playback queue.
 *
 * Mutations enqueue work and return before it completes. Commands run in submission order after
 * loading. Each update is saved before [playlists] changes. Failures are logged, preserve the last
 * published state, and do not prevent later commands. A failed load is retried on the next command
 * before any data is written.
 *
 * User scope exit cancels pending work and preserves saved data. Later mutations throw
 * [CancellationException] after argument validation.
 */
interface PlaylistRepository {
  /**
   * Saved snapshots in creation order. Initially empty until loading succeeds; an empty value does
   * not indicate whether loading has finished.
   */
  val playlists: StateFlow<List<Playlist>>

  /**
   * Appends an empty playlist with a new stable ID and a trimmed [name]. Duplicate names are
   * allowed.
   *
   * @throws IllegalArgumentException immediately if [name] is blank.
   */
  fun create(name: String)

  /**
   * Sets the playlist's trimmed [name]. A missing playlist is logged as a failed command.
   *
   * @throws IllegalArgumentException immediately if [name] is blank.
   */
  fun rename(id: PlaylistId, name: String)

  /** Deletes the playlist, preserving its episodes. A missing playlist requires no action. */
  fun delete(id: PlaylistId)

  /**
   * Appends [episodeId] unless it is already present. A missing playlist is logged as a failed
   * command.
   */
  fun addEpisode(playlistId: PlaylistId, episodeId: EpisodeId)

  /**
   * Removes membership without deleting the episode. A missing playlist or membership requires no
   * action.
   */
  fun removeEpisode(playlistId: PlaylistId, episodeId: EpisodeId)

  /**
   * Moves [episodeId] before [beforeEpisodeId], or to the end when the anchor is null. Moving an
   * episode before itself requires no action. A missing playlist, episode, or anchor is logged as a
   * failed command.
   */
  fun moveEpisode(
    playlistId: PlaylistId,
    episodeId: EpisodeId,
    beforeEpisodeId: EpisodeId?,
  )
}
