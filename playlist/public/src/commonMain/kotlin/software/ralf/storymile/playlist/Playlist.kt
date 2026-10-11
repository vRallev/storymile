package software.ralf.storymile.playlist

/** A saved playlist snapshot. Episode metadata is stored separately. */
data class Playlist(
  /** Stable identity retained across edits and reloads. */
  val id: PlaylistId,
  /** Trimmed, nonblank display name. Other playlists can use the same name. */
  val name: String,
  /** Episode identities in playback order, with no repeats within this playlist. */
  val episodeIds: List<EpisodeId>,
)
