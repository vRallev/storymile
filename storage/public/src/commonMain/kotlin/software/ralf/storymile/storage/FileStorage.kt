package software.ralf.storymile.storage

/**
 * Stores whole files within the owning scope. Names are single file names with no path separators.
 * Calls after scope exit fail with cancellation.
 */
interface FileStorage {
  /**
   * Returns storage for a child directory. [name] is one path segment. Directories are created on
   * the first write. The returned handle shares this storage's scope lifetime.
   */
  fun directory(name: String): FileStorage

  /** Returns the file contents, or null when [name] does not exist. */
  suspend fun read(name: String): ByteArray?

  /** Replaces [name] after all [content] is written. */
  suspend fun write(name: String, content: ByteArray)

  /** Deletes [name]. A missing file requires no action. */
  suspend fun delete(name: String)

  /**
   * Deletes all files and child directories in this directory. A missing directory requires no
   * action. This handle remains usable. Other directories, storage areas, and preferences are
   * unchanged. Cancellation or an I/O failure can leave the directory partly cleared.
   */
  suspend fun deleteAll()
}
