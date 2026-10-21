package software.ralf.storymile.runtimemode

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.test.runTest

class BrowserRuntimeModeStorageTest {
  @Test
  fun `browser selections persist as plain mode names`() = runTest {
    withStorage { key, storage ->
      setSavedMode(key = key, mode = "Fake")
      assertThat(storage.read()).isEqualTo("Fake")

      storage.write("Real")

      val recreatedStorage: RuntimeModeStorage = BrowserRuntimeModeStorage(key)
      assertThat(recreatedStorage.read()).isEqualTo("Real")
      assertThat(getSavedMode(key)).isEqualTo("Real")
    }
  }

  @Test
  fun `missing values stay absent until a mode is saved`() = runTest {
    withStorage { key, storage ->
      assertThat(storage.read()).isNull()
      storage.write("Fake")
      val recreatedStorage: RuntimeModeStorage = BrowserRuntimeModeStorage(key)
      assertThat(recreatedStorage.read()).isEqualTo("Fake")
    }
  }

  private suspend fun withStorage(block: suspend (String, RuntimeModeStorage) -> Unit) {
    val key = "storymile.runtime-mode-test-${Random.nextInt(Int.MAX_VALUE)}"
    try {
      block(key, BrowserRuntimeModeStorage(key))
    } finally {
      removeSavedMode(key)
    }
  }
}

@JsFun("(key, mode) => localStorage.setItem(key, mode)")
private external fun setSavedMode(key: String, mode: String)

@JsFun("(key) => localStorage.getItem(key)") private external fun getSavedMode(key: String): String?

@JsFun("(key) => localStorage.removeItem(key)") private external fun removeSavedMode(key: String)
