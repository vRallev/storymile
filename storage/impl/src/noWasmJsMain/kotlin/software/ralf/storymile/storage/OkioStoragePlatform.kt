package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer
import okio.use

/**
 * Keeps namespaces separate in [rootDirectory] and [cacheDirectory].
 *
 * File replacement uses [FileSystem.atomicMove]. On FAT and NTFS, replacement can have a gap
 * between deleting the old file and moving the new file.
 */
abstract class OkioStoragePlatform(
  private val fileSystem: FileSystem,
  private val ioDispatcher: CoroutineDispatcher,
) : StoragePlatform() {
  /** Returns an absolute directory reserved for Storymile storage. */
  protected abstract fun rootDirectory(): Path

  /** Returns an absolute cache directory reserved for Storymile. */
  protected abstract fun cacheDirectory(): Path

  internal final override fun createPreferences(
    namespace: String,
    name: String,
    coroutineScope: CoroutineScope,
  ): DataStore<Preferences> {
    return PreferenceDataStoreFactory.createWithPath(
      scope = CoroutineScope(coroutineScope.coroutineContext + ioDispatcher),
      produceFile = {
        requireNotNull(storageFile(namespace, "preferences", "$name.preferences_pb", create = true))
      },
    )
  }

  internal final override suspend fun readFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ): ByteArray? {
    return withContext(ioDispatcher) {
      val path = storageFile(namespace, area, name, create = false) ?: return@withContext null
      if (fileSystem.metadataOrNull(path) == null) {
        return@withContext null
      }
      fileSystem.read(path) { readByteArray() }
    }
  }

  internal final override suspend fun writeFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    content: ByteArray,
  ) {
    withContext(ioDispatcher) {
      val path = requireNotNull(storageFile(namespace, area, name, create = true))
      val temporaryPath =
        requireNotNull(path.parent) / ".${Random.nextLong().toULong().toString(16)}.tmp"
      val sink = fileSystem.sink(temporaryPath, mustCreate = true)
      try {
        sink.buffer().use { it.write(content) }
        coroutineContext.ensureActive()
        fileSystem.atomicMove(temporaryPath, path)
      } finally {
        fileSystem.delete(temporaryPath)
      }
    }
  }

  internal final override suspend fun deleteFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
  ) {
    withContext(ioDispatcher) {
      val path = storageFile(namespace, area, name, create = false) ?: return@withContext
      coroutineContext.ensureActive()
      fileSystem.delete(path)
    }
  }

  internal final override suspend fun deleteAllFiles(
    namespace: String,
    area: FileStorageArea,
    path: String,
  ) {
    withContext(ioDispatcher) {
      val segments = if (path.isEmpty()) emptyList() else path.split('/')
      val directory =
        storageDirectory(namespace, area.directoryName, segments, create = false, rootFor(area))
          ?: return@withContext
      for (entry in fileSystem.list(directory)) {
        deleteTree(entry)
      }
    }
  }

  private suspend fun deleteTree(path: Path) {
    currentCoroutineContext().ensureActive()
    val metadata = fileSystem.metadataOrNull(path) ?: return
    if (metadata.symlinkTarget == null && metadata.isDirectory) {
      for (entry in fileSystem.list(path)) {
        deleteTree(entry)
      }
    }
    currentCoroutineContext().ensureActive()
    fileSystem.delete(path)
  }

  private fun storageFile(
    namespace: String,
    area: FileStorageArea,
    name: String,
    create: Boolean,
  ): Path? {
    return storageFile(namespace, area.directoryName, name, create, rootFor(area))
  }

  private fun storageFile(
    namespace: String,
    area: String,
    name: String,
    create: Boolean,
    root: Path = rootDirectory(),
  ): Path? {
    val segments = name.split('/')
    val directory =
      storageDirectory(namespace, area, segments.dropLast(1), create, root) ?: return null
    val path = directory / segments.last()
    val metadata = fileSystem.metadataOrNull(path)
    check(metadata == null || (metadata.symlinkTarget == null && metadata.isRegularFile)) {
      "Storage path must be a regular file: $path"
    }
    return path
  }

  private fun storageDirectory(
    namespace: String,
    area: String,
    segments: List<String>,
    create: Boolean,
    root: Path,
  ): Path? {
    check(root.isAbsolute) { "Storage root must be absolute." }
    if (!ensureDirectory(root, create, createParents = true)) {
      return null
    }
    val namespaceDirectory = root / namespace
    if (!ensureDirectory(namespaceDirectory, create)) {
      return null
    }
    val areaDirectory = namespaceDirectory / area
    if (!ensureDirectory(areaDirectory, create)) {
      return null
    }
    var directory = areaDirectory
    for (segment in segments) {
      directory /= segment
      if (!ensureDirectory(directory, create)) {
        return null
      }
    }
    return directory
  }

  private fun rootFor(area: FileStorageArea): Path =
    when (area) {
      FileStorageArea.Files -> rootDirectory()
      FileStorageArea.Cache -> cacheDirectory()
    }

  private fun ensureDirectory(
    path: Path,
    create: Boolean,
    createParents: Boolean = false,
  ): Boolean {
    val metadata = fileSystem.metadataOrNull(path)
    if (metadata != null) {
      check(metadata.symlinkTarget == null && metadata.isDirectory) {
        "Storage path must be a directory: $path"
      }
      return true
    }
    if (!create) {
      return false
    }
    if (createParents) {
      fileSystem.createDirectories(path)
    } else {
      fileSystem.createDirectory(path)
    }
    return true
  }
}
