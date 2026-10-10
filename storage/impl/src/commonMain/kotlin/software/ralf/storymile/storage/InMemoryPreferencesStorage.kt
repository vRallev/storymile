package software.ralf.storymile.storage

import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage as DataStoreStorage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class InMemoryPreferencesStorage : DataStoreStorage<Preferences> {
  private val data = MutableStateFlow(emptyPreferences())
  private val connected = MutableStateFlow(false)
  private val transactionMutex = Mutex()
  private val coordinator = Coordinator()

  override fun createConnection(): StorageConnection<Preferences> {
    check(connected.compareAndSet(expect = false, update = true)) {
      "Preferences storage already has an active connection."
    }
    return Connection()
  }

  private inner class Connection : StorageConnection<Preferences> {
    private val closed = MutableStateFlow(false)

    override val coordinator: InterProcessCoordinator
      get() = this@InMemoryPreferencesStorage.coordinator

    override suspend fun <R> readScope(
      block: suspend ReadScope<Preferences>.(locked: Boolean) -> R,
    ): R {
      checkNotClosed()
      val locked = transactionMutex.tryLock()
      val transaction = Transaction(data.value)
      try {
        return block(transaction, locked)
      } finally {
        transaction.close()
        if (locked) {
          transactionMutex.unlock()
        }
      }
    }

    override suspend fun writeScope(block: suspend WriteScope<Preferences>.() -> Unit) {
      checkNotClosed()
      transactionMutex.withLock {
        checkNotClosed()
        val transaction = Transaction(data.value)
        try {
          block(transaction)
          checkNotClosed()
          data.value = transaction.preferences
        } finally {
          transaction.close()
        }
      }
    }

    override fun close() {
      if (closed.compareAndSet(expect = false, update = true)) {
        connected.value = false
      }
    }

    private fun checkNotClosed() {
      check(!closed.value) { "Preferences storage connection is closed." }
    }
  }

  private class Transaction(initialData: Preferences) : WriteScope<Preferences> {
    private val closed = MutableStateFlow(false)

    var preferences = initialData
      private set

    override suspend fun readData(): Preferences {
      checkNotClosed()
      return preferences
    }

    override suspend fun writeData(value: Preferences) {
      checkNotClosed()
      preferences = value.toPreferences()
    }

    override fun close() {
      closed.value = true
    }

    private fun checkNotClosed() {
      check(!closed.value) { "Preferences transaction is closed." }
    }
  }

  private class Coordinator : InterProcessCoordinator {
    private val mutex = Mutex()
    private val versionMutex = Mutex()
    private var version = 0

    override val updateNotifications: Flow<Unit> = emptyFlow()

    override suspend fun <T> lock(block: suspend () -> T): T = mutex.withLock { block() }

    override suspend fun <T> tryLock(block: suspend (Boolean) -> T): T {
      val locked = mutex.tryLock()
      try {
        return block(locked)
      } finally {
        if (locked) {
          mutex.unlock()
        }
      }
    }

    override suspend fun getVersion(): Int = versionMutex.withLock { version }

    override suspend fun incrementAndGetVersion(): Int = versionMutex.withLock { ++version }
  }
}
