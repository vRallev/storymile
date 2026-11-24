package software.ralf.storymile.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import software.ralf.storymile.storage.Storage
import software.ralf.storymile.storage.testing.FakeStorage

class StoredThemeControllerTest {
  @Test
  fun `theme choices survive reopening and can return to System`() = runTest {
    val preferences = InMemoryPreferences()
    val storage =
      object : Storage by FakeStorage() {
        override fun preferences(name: String): DataStore<Preferences> = preferences
      }

    suspend fun reopen(): ThemeController =
      StoredThemeController(storage = storage, coroutineScope = backgroundScope).also {
        it.awaitInitialMode()
      }

    val original = reopen()
    assertThat(original.mode.value).isEqualTo(ThemeMode.SYSTEM)
    original.setMode(ThemeMode.DARK)
    runCurrent()

    val dark = reopen()
    assertThat(dark.mode.value).isEqualTo(ThemeMode.DARK)
    dark.toggle(systemDarkTheme = false)
    runCurrent()

    val light = reopen()
    assertThat(light.mode.value).isEqualTo(ThemeMode.LIGHT)
    light.setMode(ThemeMode.SYSTEM)
    runCurrent()

    val system = reopen()
    assertThat(system.mode.value).isEqualTo(ThemeMode.SYSTEM)
    system.toggle(systemDarkTheme = true)
    runCurrent()
    assertThat(system.mode.value).isEqualTo(ThemeMode.LIGHT)
  }

  private class InMemoryPreferences : DataStore<Preferences> {
    override val data: StateFlow<Preferences>
      field = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
      data.value = transform(data.value)
      return data.value
    }
  }
}
