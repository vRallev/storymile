package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class InMemoryPreferencesDataStore : DataStore<Preferences> {
  private val mutex = Mutex()
  private val updateContext = UpdateContext()

  override val data: StateFlow<Preferences>
    field = MutableStateFlow(emptyPreferences())

  override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
    check(currentCoroutineContext()[updateContext.key] == null) {
      "Nested updates to the same preferences store are not supported."
    }
    return withContext(updateContext) {
      mutex.withLock {
        val updated = transform(data.value).toPreferences()
        currentCoroutineContext().ensureActive()
        data.value = updated
        updated
      }
    }
  }

  private class UpdateContext : CoroutineContext.Element {
    override val key = object : CoroutineContext.Key<UpdateContext> {}
  }
}
