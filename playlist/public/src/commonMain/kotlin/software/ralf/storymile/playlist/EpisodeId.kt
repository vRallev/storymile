package software.ralf.storymile.playlist

import kotlin.jvm.JvmInline

/** Stable identity of an episode, independent of its playlist membership. */
@JvmInline
value class EpisodeId(
  /** Nonblank identity shared by every playlist that contains this episode. */
  val value: String,
) {
  init {
    require(value.isNotBlank()) { "Episode ID must not be blank." }
  }
}
