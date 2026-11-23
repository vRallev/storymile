package software.ralf.storymile.playlist

/** Supplies the playlists for a user with no saved playlist file. */
fun interface InitialPlaylists {
  /** Returns the initial playlists in display order. */
  fun get(): List<Playlist>
}
