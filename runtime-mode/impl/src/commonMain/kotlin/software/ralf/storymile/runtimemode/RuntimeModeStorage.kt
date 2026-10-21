package software.ralf.storymile.runtimemode

/** Platform persistence for the saved runtime mode. */
interface RuntimeModeStorage {
  /** Reads the saved value synchronously, or returns null if none exists. */
  fun read(): String?

  /** Persists the value before an application restart. */
  suspend fun write(mode: String)
}
