package software.ralf.storymile.storage

/** Manages database files in the browser SQLite virtual filesystem. */
interface BrowserStorageDatabases {
  /** Returns whether SQLite has any files belonging to the hex-encoded namespace. */
  suspend fun hasDatabases(encodedNamespace: String): Boolean

  /** Removes SQLite files belonging to the hex-encoded namespace after its databases close. */
  suspend fun deleteDatabases(encodedNamespace: String)
}
