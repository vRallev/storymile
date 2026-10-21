package software.ralf.storymile.runtimemode

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class BrowserRuntimeModeStorage internal constructor(private val key: String) : RuntimeModeStorage {
  @Inject constructor() : this("storymile.runtime-mode")

  override fun read(): String? = readSavedMode(key)

  override suspend fun write(mode: String) {
    writeSavedMode(key = key, mode = mode)
  }
}

@JsFun("(key) => localStorage.getItem(key)")
private external fun readSavedMode(key: String): String?

@JsFun("(key, mode) => localStorage.setItem(key, mode)")
private external fun writeSavedMode(key: String, mode: String)
