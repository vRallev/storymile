package software.ralf.storymile.playlist

import kotlin.jvm.JvmInline

/** A trimmed playlist display name that contains text. */
@JvmInline
value class PlaylistName
private constructor(
  /** Normalized display text. Internal whitespace is preserved. */
  val value: String,
) {
  init {
    require(value.isNotBlank() && value == value.trim()) {
      "Playlist name must be trimmed and contain text."
    }
  }

  /** Creates normalized playlist names. */
  companion object {
    /**
     * Removes surrounding whitespace from [value].
     *
     * @throws IllegalArgumentException if [value] is blank.
     */
    operator fun invoke(value: String): PlaylistName = PlaylistName(value.trim())
  }
}
