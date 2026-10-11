package software.ralf.storymile.playlist

import kotlin.jvm.JvmInline

/** Stable identity of a saved playlist. */
@JvmInline
value class PlaylistId(
  /** Nonblank identity retained when the playlist changes. */
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "Playlist ID must not be blank." }
  }
}
