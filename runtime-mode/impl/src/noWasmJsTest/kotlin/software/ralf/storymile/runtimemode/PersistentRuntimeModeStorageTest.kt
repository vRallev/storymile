package software.ralf.storymile.runtimemode

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import kotlin.test.Test
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import software.ralf.app.platform.scope.coroutine.coroutineScope
import software.ralf.app.platform.scope.runTestWithScope
import software.ralf.storymile.restart.ApplicationRestarter
import software.ralf.storymile.storage.FileStorage
import software.ralf.storymile.storage.Storage

class PersistentRuntimeModeStorageTest {
  @Test
  fun `concurrent property reads block until the saved mode loads and then use the cache`() =
    runTestWithScope { scope ->
      val preferences = DelayedPreferences()
      val storage =
        PersistentRuntimeModeStorage(
          factory = TestStorageFactory(preferences),
          coroutineScope = scope.coroutineScope(),
        )
      val controller: RuntimeModeController =
        RuntimeModeControllerImpl(
          storage = storage,
          applicationRestarter =
            object : ApplicationRestarter {
              override fun restart() = error("Reading the mode must not restart the app")
            },
        )

      val reads = List(2) { async(Dispatchers.Default) { controller.mode } }
      try {
        preferences.readStarted.await()
        reads.forEach { assertThat(it.isCompleted).isFalse() }
      } finally {
        preferences.allowRead.complete(Unit)
      }

      assertThat(reads.awaitAll()).isEqualTo(listOf(RuntimeMode.Fake, RuntimeMode.Fake))
      assertThat(controller.mode).isEqualTo(RuntimeMode.Fake)
      assertThat(preferences.readCount).isEqualTo(1)
    }

  private class TestStorageFactory(private val preferences: DataStore<Preferences>) :
    Storage.Factory {
    override fun create(
      namespace: String,
      coroutineScope: CoroutineScope,
      mustBePersistent: Boolean,
    ): Storage {
      check(namespace == "runtime-mode")
      check(mustBePersistent) { "Runtime mode must persist in fake mode." }
      return object : Storage {
        override val files: FileStorage
          get() = error("Not used")

        override val cache: FileStorage
          get() = error("Not used")

        override suspend fun preferences(name: String): DataStore<Preferences> {
          check(name == "runtime_mode")
          return preferences
        }

        override suspend fun databasePath(name: String): String? = error("Not used")
      }
    }
  }

  private class DelayedPreferences : DataStore<Preferences> {
    val readStarted = CompletableDeferred<Unit>()
    val allowRead = CompletableDeferred<Unit>()
    var readCount = 0
      private set

    override val data: Flow<Preferences> = flow {
      readStarted.complete(Unit)
      allowRead.await()
      readCount++
      emit(mutablePreferencesOf(stringPreferencesKey("runtime_mode") to "Fake"))
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
      error("Not used")
  }
}
