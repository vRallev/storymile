package software.ralf.storymile.storage

import okio.Path

/**
 * Stores whole files within the owning scope. Names are single file names with no path separators.
 * File operations after scope exit fail with cancellation.
 */
interface FileStorage {
  /**
   * Avoid this API when Wasm support is required. Use [read] and [write] instead.
   *
   * This directory's absolute path, or null for in-memory and browser storage. Creates the
   * directory on first access and caches the path. The path remains available after scope exit. Use
   * a dedicated child directory for library-owned files. Close the library before clearing that
   * directory or leaving the scope. Direct filesystem access bypasses namespace ownership, scope
   * cancellation, and file-name encoding.
   */
  val path: Path?

  /**
   * Returns storage for a child directory. [name] is one path segment. Directories are created on
   * the first write or first access to [path]. The returned handle shares this storage's scope
   * lifetime.
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
