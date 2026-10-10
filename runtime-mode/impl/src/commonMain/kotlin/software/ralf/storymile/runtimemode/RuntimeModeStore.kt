package software.ralf.storymile.runtimemode

interface RuntimeModeStore {
  suspend fun readModeName(): String?

  suspend fun writeModeName(modeName: String)
}
