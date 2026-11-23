package software.ralf.storymile.player

/** Maps an episode enclosure to a source the platform audio player can open. */
fun interface AudioSourceResolver {
  /** Returns [enclosureUrl] when no alternate source applies. */
  fun resolve(enclosureUrl: String): String
}
