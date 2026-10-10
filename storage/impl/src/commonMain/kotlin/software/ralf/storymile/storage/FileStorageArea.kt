package software.ralf.storymile.storage

internal enum class FileStorageArea(val directoryName: String) {
  Files("files"),
  Cache("cache"),
}
