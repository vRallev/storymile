package software.ralf.storymile.playlist

/** A saved playlist snapshot. Episode metadata is stored separately. */
data class Playlist(
  /** Stable identity retained across edits and reloads. */
  val id: PlaylistId,
  /** Display name. Other playlists can use the same name. */
  val name: PlaylistName,
  /** Episode identities in playback order, with no repeats within this playlist. */
  val episodeIds: List<EpisodeId>,
)
