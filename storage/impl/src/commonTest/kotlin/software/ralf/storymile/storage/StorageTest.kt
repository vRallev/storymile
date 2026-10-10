@file:OptIn(ExperimentalCoroutinesApi::class)

package software.ralf.storymile.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import software.ralf.app.platform.scope.Scope
import software.ralf.app.platform.scope.buildTestScope
import software.ralf.app.platform.scope.coroutine.CoroutineScopeScoped
import software.ralf.app.platform.scope.coroutine.addCoroutineScopeScoped
import software.ralf.app.platform.scope.runTestWithScope

class StorageTest {
  @Test
  fun `one storage instance reuses its named preferences store`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform()
    val storage: Storage = scope.storage("app", platform)

    assertSame(storage.preferences("settings"), storage.preferences("settings"))
  }

  @Test
  fun `different active scopes cannot share a persistent namespace`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform()
    val otherScope = Scope.buildTestScope(this, name = scope.name)
    scope.storage("app", platform).files.read("file")

    val other: Storage = otherScope.storage("app", platform)
    assertFailsWith<IllegalStateException> { other.files.read("file") }
  }

  @Test
  fun `scope exit cancels retained file preferences and flow handles`() =
    runTestWithScope { scope ->
      val platform = InMemoryPlatform()
      val storage: Storage = scope.storage("app", platform)
      val preferences = storage.preferences("settings")
      val directory = storage.files.directory("books").directory("chapters")
      val collecting = async {
        assertFailsWith<CancellationException> { preferences.data.collect {} }
      }
      runCurrent()

      scope.destroy()

      collecting.await()
      assertFailsWith<CancellationException> { storage.files.read("file") }
      assertFailsWith<CancellationException> { storage.files.write("file", byteArrayOf(1)) }
      assertFailsWith<CancellationException> { storage.files.delete("file") }
      assertFailsWith<CancellationException> { storage.files.deleteAll() }
      assertFailsWith<CancellationException> { storage.files.directory("books") }
      assertFailsWith<CancellationException> { directory.read("chapter") }
      assertFailsWith<CancellationException> { storage.cache.read("file") }
      assertFailsWith<CancellationException> { storage.preferences("settings") }
      assertFailsWith<CancellationException> { preferences.updateData { it } }
      assertFailsWith<CancellationException> { preferences.data.first() }
      assertFailsWith<IllegalStateException> { scope.storage("app", platform) }
    }

  @Test
  fun `destroying the parent stops storage owned by its children`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform()
    val userScope = scope.child(this)
    val storage: Storage = userScope.storage("user-alice", platform)

    scope.destroy()

    assertFailsWith<CancellationException> { storage.files.read("file") }
    assertFailsWith<CancellationException> { storage.preferences("settings") }
  }

  @Test
  fun `scope exit cancels a pending file write`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform(blockWrites = true)
    val storage: Storage = scope.storage("app", platform)
    val writing = async {
      assertFailsWith<CancellationException> { storage.files.write("file", byteArrayOf(1)) }
    }
    platform.writeStarted.await()

    scope.destroy()

    writing.await()
    platform.writeCanceled.await()
  }

  @Test
  fun `caller cancellation cancels its file write and keeps storage usable`() =
    runTestWithScope { scope ->
      val platform = InMemoryPlatform(blockWrites = true)
      val storage: Storage = scope.storage("app", platform)
      val writing = async { storage.files.write("file", byteArrayOf(1)) }
      platform.writeStarted.await()

      writing.cancel()
      writing.join()

      platform.writeCanceled.await()
      storage.files.delete("file")
      storage.preferences("settings").data.first()
    }

  @Test
  fun `clearing a directory waits for writes through another handle`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform(blockWrites = true)
    val storage: Storage = scope.storage("app", platform)
    val writing = async { storage.files.directory("books").write("chapter", byteArrayOf(1)) }
    platform.writeStarted.await()
    val clearing = async { storage.files.directory("books").deleteAll() }
    runCurrent()

    assertFalse(clearing.isCompleted)
    platform.finishWrite.complete(Unit)
    writing.await()
    clearing.await()

    assertNull(storage.files.directory("books").read("chapter"))
  }

  @Test
  fun `reopening a namespace waits for canceled work to finish cleanup`() =
    runTestWithScope { scope ->
      val cleanup = CompletableDeferred<Unit>()
      val platform = InMemoryPlatform(blockWrites = true, cleanup = cleanup)
      val previousScope = scope.child(this)
      val previous: Storage = previousScope.storage("user-alice", platform)
      val writing = async {
        assertFailsWith<CancellationException> { previous.files.write("file", byteArrayOf(1)) }
      }
      platform.writeStarted.await()

      previousScope.destroy()
      val reopened: Storage = scope.child(this).storage("user-alice", platform)
      val reading = async { reopened.files.read("file") }
      runCurrent()

      assertFalse(reading.isCompleted)
      cleanup.complete(Unit)
      writing.await()
      assertNull(reading.await())
    }

  @Test
  fun `names cannot escape their namespace or storage area`() = runTestWithScope { scope ->
    val platform = InMemoryPlatform()
    val storage: Storage = scope.storage("app", platform)
    val invalidNames =
      listOf("", " ", ".", "..", "../outside", "/outside", "C:\\outside", "a\u0000b")

    invalidNames.forEach { name ->
      assertFailsWith<IllegalArgumentException> { scope.storage(name, platform) }
      assertFailsWith<IllegalArgumentException> { storage.preferences(name) }
      assertFailsWith<IllegalArgumentException> { storage.files.read(name) }
      assertFailsWith<IllegalArgumentException> { storage.files.write(name, byteArrayOf(1)) }
      assertFailsWith<IllegalArgumentException> { storage.files.delete(name) }
      assertFailsWith<IllegalArgumentException> { storage.files.directory(name) }
    }
  }

  private fun Scope.child(testScope: TestScope): Scope {
    return buildChild("user") {
      addCoroutineScopeScoped(
        CoroutineScopeScoped(
          testScope.backgroundScope.coroutineContext +
            SupervisorJob(testScope.backgroundScope.coroutineContext.job) +
            CoroutineName("user"),
        ),
      )
    }
  }

  private class InMemoryPlatform(
    private val blockWrites: Boolean = false,
    private val cleanup: CompletableDeferred<Unit>? = null,
  ) : StoragePlatform() {
    val writeStarted = CompletableDeferred<Unit>()
    val writeCanceled = CompletableDeferred<Unit>()
    val finishWrite = CompletableDeferred<Unit>()
    private val files = mutableMapOf<Triple<String, FileStorageArea, String>, ByteArray>()

    override fun createPreferences(
      namespace: String,
      name: String,
      coroutineScope: CoroutineScope,
    ): DataStore<Preferences> = InMemoryPreferences()

    override suspend fun readFile(
      namespace: String,
      area: FileStorageArea,
      name: String,
    ): ByteArray? {
      return files[Triple(namespace, area, name)]?.copyOf()
    }

    override suspend fun writeFile(
      namespace: String,
      area: FileStorageArea,
      name: String,
      content: ByteArray,
    ) {
      if (blockWrites) {
        writeStarted.complete(Unit)
        try {
          finishWrite.await()
        } finally {
          withContext(NonCancellable) { cleanup?.await() }
          writeCanceled.complete(Unit)
        }
      }
      files[Triple(namespace, area, name)] = content.copyOf()
    }

    override suspend fun deleteFile(namespace: String, area: FileStorageArea, name: String) {
      files.remove(Triple(namespace, area, name))
    }

    override suspend fun deleteAllFiles(namespace: String, area: FileStorageArea, path: String) {
      files.keys.removeAll { key ->
        key.first == namespace &&
          key.second == area &&
          (path.isEmpty() || key.third.startsWith("$path/"))
      }
    }
  }

  private class InMemoryPreferences : DataStore<Preferences> {
    private val mutex = Mutex()

    override val data: Flow<Preferences>
      field = MutableStateFlow(emptyPreferences())

    override suspend fun updateData(
      transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
      return mutex.withLock { transform(data.value).also { data.value = it } }
    }
  }
}
